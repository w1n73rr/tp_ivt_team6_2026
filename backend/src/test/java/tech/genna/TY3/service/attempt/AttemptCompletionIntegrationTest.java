package tech.genna.TY3.service.attempt;

import org.junit.jupiter.api.Test;
import java.util.*;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;

class AttemptCompletionIntegrationTest extends AttemptIntegrationTest {
    @Test void completeIsIdempotentAndAnswersBecomeReadOnly() throws Exception {
        UUID id=start();String base="/api/attempts/"+id;
        call(token,"PUT",base+"/answers/"+q,Map.of("selectedOptionIds",List.of(x)),200);
        var response=call(token,"POST",base+"/complete",Map.of("score",0,"status","IN_PROGRESS"),200);
        var result=json.readTree(response.body());
        assertEquals(1,result.path("correctCount").asInt());assertEquals(1,result.path("score").asInt());
        assertEquals(0,result.path("incorrectCount").asInt());assertEquals(0,result.path("skippedCount").asInt());
        assertEquals(100,result.path("scorePercent").asDouble());
        assertFalse(response.body().contains("correctOptionIds"));
        assertEquals(response.body(),call(token,"POST",base+"/complete",null,200).body());
        call(token,"PUT",base+"/answers/"+q,Map.of("selectedOptionIds",List.of(y)),409);
        assertEquals("COMPLETED",json.readTree(call(token,"GET",base,null,200).body()).path("attempt").path("status").asText());
        assertEquals(call(otherToken,"POST",base+"/complete",null,404).body(),
                call(otherToken,"POST","/api/attempts/"+UUID.randomUUID()+"/complete",null,404).body());
    }
    @Test void databaseFailureRollsBackStatusTimeAndResult() throws Exception {
        UUID id=start();
        jdbc.execute("ALTER TABLE attempts ADD CONSTRAINT attempt_test_failure CHECK (user_id <> '"+owner+"'::uuid OR status <> 'COMPLETED')");
        try {
            call(token,"POST","/api/attempts/"+id+"/complete",null,503);
            assertEquals("IN_PROGRESS",jdbc.queryForObject("SELECT status FROM attempts WHERE id=?",String.class,id));
            assertNull(jdbc.queryForObject("SELECT result_json FROM attempts WHERE id=?",String.class,id));
            assertNull(jdbc.queryForObject("SELECT completed_at FROM attempts WHERE id=?",java.time.OffsetDateTime.class,id));
        } finally {jdbc.execute("ALTER TABLE attempts DROP CONSTRAINT attempt_test_failure");}
        call(token,"POST","/api/attempts/"+id+"/complete",null,200);
    }
    @Test void crossingAnswerAndCompletionAgreeWithCommittedSnapshot() throws Exception {
        for(int n=0;n<4;n++) {
            UUID id=start();String base="/api/attempts/"+id;
            var start=new CountDownLatch(1);
            try(var pool=Executors.newVirtualThreadPerTaskExecutor()) {
                var answer=pool.submit(()->{
                    start.await();
                    var response=call(token,"PUT",base+"/answers/"+q,Map.of("selectedOptionIds",List.of(x)),0);
                    assertTrue(response.statusCode()==200 || response.statusCode()==409);
                    if(response.statusCode()==409) assertEquals("ATTEMPT_CLOSED",json.readTree(response.body()).path("code").asText());
                    return response.statusCode()==200;
                });
                var completion=pool.submit(()->{start.await();return call(token,"POST",base+"/complete",null,200);});
                start.countDown();
                boolean accepted=answer.get(30,TimeUnit.SECONDS);
                var result=json.readTree(completion.get(30,TimeUnit.SECONDS).body());
                assertEquals(accepted?1:0,result.path("correctCount").asInt());
                assertEquals(accepted?0:1,result.path("skippedCount").asInt());
                assertEquals(0,result.path("incorrectCount").asInt());
                assertEquals(result,json.readTree(call(token,"POST",base+"/complete",null,200).body()));
            }
        }
    }
}
