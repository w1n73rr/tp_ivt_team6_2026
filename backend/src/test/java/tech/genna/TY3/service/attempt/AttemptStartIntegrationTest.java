package tech.genna.TY3.service.attempt;

import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import java.net.http.HttpResponse;
import java.util.*;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;

class AttemptStartIntegrationTest extends AttemptIntegrationTest {
    private String path() {return "/api/quizzes/"+quiz+"/attempts";}
    private UUID attemptId(HttpResponse<String> response) {
        assertPublic(response);
        return UUID.fromString(json.readTree(response.body()).path("attempt").path("id").asText());
    }
    private void assertPublic(HttpResponse<String> response) {
        assertTrue(response.headers().firstValue("Cache-Control").orElse("").contains("no-store"));
        for(String field:List.of("correctOptionIds","correct","score","scorePercent","explanation","sourceId"))
            assertFalse(response.body().contains("\""+field+"\""),"Private field leaked: "+field);
    }
    @Test void parallelStartsWithSameAndDifferentKeysCreateExactlyOneActiveAttempt() throws Exception {
        UUID shared=UUID.randomUUID();var gate=new CountDownLatch(1);
        try(var pool=Executors.newVirtualThreadPerTaskExecutor()) {
            var futures=new ArrayList<Future<HttpResponse<String>>>();
            for(int n=0;n<8;n++) {
                UUID key=n<4?shared:UUID.randomUUID();
                futures.add(pool.submit(()->{gate.await();return call(token,"POST",path(),null,0,key);}));
            }
            gate.countDown();var ids=new HashSet<UUID>();int created=0;
            for(var future:futures) {
                var response=future.get(30,TimeUnit.SECONDS);
                assertTrue(response.statusCode()==200 || response.statusCode()==201);
                if(response.statusCode()==201) created++;
                ids.add(attemptId(response));
            }
            assertEquals(1,ids.size());assertEquals(1,created);
            assertEquals(1,jdbc.queryForObject("SELECT count(*) FROM attempts WHERE user_id=? AND quiz_id=? AND status='IN_PROGRESS'",Integer.class,owner,quiz));
            assertEquals(5,jdbc.queryForObject("SELECT count(*) FROM attempt_start_requests WHERE user_id=?",Integer.class,owner));
        }
    }
    @Test void resumePreservesSnapshotAndRepeatStartsEmptyWithoutChangingOldAttempt() throws Exception {
        UUID first=attemptId(call(token,"POST",path(),null,201));
        call(token,"PUT","/api/attempts/"+first+"/answers/"+q,Map.of("selectedOptionIds",List.of(x)),200);
        call(token,"PATCH","/api/quizzes/"+quiz,Map.of("name","New name"),200);
        UUID resumeKey=UUID.randomUUID();
        var resumed=call(token,"POST",path(),null,200,resumeKey);
        assertEquals(first,attemptId(resumed));
        var body=json.readTree(resumed.body());
        assertEquals("Snapshot",body.path("attempt").path("quiz").path("name").asText());
        assertEquals(x.toString(),body.path("answers").get(0).path("selectedOptionIds").get(0).asText());
        var continued=call(token,"GET","/api/attempts/"+first,null,200);
        assertPublic(continued);assertEquals(body,json.readTree(continued.body()));
        // Use the existing production completion endpoint after recovering the missing features in dev.
        call(token,"POST","/api/attempts/"+first+"/complete",null,200);
        UUID second=attemptId(call(token,"POST",path(),null,201));
        assertNotEquals(first,second);
        var fresh=json.readTree(call(token,"GET","/api/attempts/"+second,null,200).body());
        assertTrue(fresh.path("answers").isEmpty());assertEquals(0,fresh.path("attempt").path("answeredCount").asInt());
        assertEquals("New name",fresh.path("attempt").path("quiz").path("name").asText());
        assertEquals(first,attemptId(call(token,"POST",path(),null,200,resumeKey)),"A resume key must remain bound to the original attempt");
        assertEquals(second,attemptId(call(token,"POST",path(),null,200)));
        assertEquals(1,json.readTree(call(token,"GET","/api/attempts/"+first,null,200).body()).path("answers").size());
    }
    @Test void twoUsersCannotStartOrContinueEachOthersAttempt() throws Exception {
        UUID id=attemptId(call(token,"POST",path(),null,201));
        call(null,"POST",path(),null,401);call(null,"GET","/api/attempts/"+id,null,401);
        assertEquals(call(otherToken,"POST",path(),null,404).body(),
                call(otherToken,"POST","/api/quizzes/"+UUID.randomUUID()+"/attempts",null,404).body());
        assertEquals(call(otherToken,"GET","/api/attempts/"+id,null,404).body(),
                call(otherToken,"GET","/api/attempts/"+UUID.randomUUID(),null,404).body());
        assertPublic(call(token,"GET","/api/quizzes/"+quiz,null,200));
        assertEquals(0,jdbc.queryForObject("SELECT count(*) FROM attempts WHERE user_id=?",Integer.class,other));
    }
    @Test void requestKeysAreScopedToOwner() throws Exception {
        UUID otherQuiz=UUID.randomUUID(),otherJob=UUID.randomUUID(),sharedKey=UUID.randomUUID();
        jdbc.update("""
                INSERT INTO generation_jobs(id,user_id,request_json,material_id,file_id,format,material_name,subject_id,subject_name,status)
                SELECT ?,?,request_json,material_id,file_id,format,material_name,subject_id,subject_name,status
                FROM generation_jobs WHERE id=?
                """,otherJob,other,job);
        jdbc.update("""
                INSERT INTO quizzes(id,job_id,user_id,summary_json,result_json)
                SELECT ?,?,?,jsonb_set(summary_json::jsonb,'{id}',to_jsonb(?::text))::text,result_json
                FROM quizzes WHERE id=?
                """,otherQuiz,otherJob,other,otherQuiz.toString(),quiz);
        UUID first=attemptId(call(token,"POST",path(),null,201,sharedKey));
        UUID second=attemptId(call(otherToken,"POST","/api/quizzes/"+otherQuiz+"/attempts",null,201,sharedKey));
        assertNotEquals(first,second);
        call(token,"GET","/api/attempts/"+second,null,404);
        call(otherToken,"GET","/api/attempts/"+first,null,404);
    }
    @Test void databaseRejectsDuplicateActiveAttemptsAndBindingFailureRollsBackCreation() throws Exception {
        UUID id=start();
        assertThrows(DataIntegrityViolationException.class,()->jdbc.update("""
                INSERT INTO attempts(id,user_id,quiz_id,idempotency_key,quiz_json,questions_json,started_at)
                SELECT ?,user_id,quiz_id,?,quiz_json,questions_json,started_at FROM attempts WHERE id=?
                """,UUID.randomUUID(),UUID.randomUUID().toString(),id));
        jdbc.update("DELETE FROM attempts WHERE id=?",id);
        assertEquals(0,jdbc.queryForObject("SELECT count(*) FROM attempt_start_requests WHERE user_id=?",Integer.class,owner));
        jdbc.execute("ALTER TABLE attempt_start_requests ADD CONSTRAINT reject_test_start_key CHECK (user_id <> '"+owner+"'::uuid)");
        try {
            call(token,"POST",path(),null,503);
            assertEquals(0,jdbc.queryForObject("SELECT count(*) FROM attempts WHERE user_id=?",Integer.class,owner));
        } finally {jdbc.execute("ALTER TABLE attempt_start_requests DROP CONSTRAINT reject_test_start_key");}
        call(token,"POST",path(),null,201);
    }
}
