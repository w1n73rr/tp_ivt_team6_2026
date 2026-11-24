package tech.genna.TY3.service.attempt;

import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class AttemptResultIntegrationTest extends AttemptIntegrationTest {
    @Test void resultHasOrderedQuestionsAndDisjointCounters() throws Exception {
        UUID q2=UUID.randomUUID(),q3=UUID.randomUUID(),a2=UUID.randomUUID(),b2=UUID.randomUUID();
        var source=json.readTree(jdbc.queryForObject("SELECT result_json FROM quizzes WHERE id=?",String.class,quiz));
        var questions=(tools.jackson.databind.node.ArrayNode)source.get("questions");
        for(var next:List.of(q2,q3)) {
            var question=questions.addObject().put("id",next.toString()).put("type",next.equals(q2)?"multiple_choice":"true_false").put("text","Next");
            var options=question.putArray("options");
            options.addObject().put("id",a2.toString()).put("text","A");
            options.addObject().put("id",b2.toString()).put("text","B");
            question.putArray("correctOptionIds").add(a2.toString());
            if(next.equals(q2)) ((tools.jackson.databind.node.ArrayNode)question.get("correctOptionIds")).add(b2.toString());
        }
        jdbc.update("UPDATE quizzes SET result_json=?,summary_json=jsonb_set(summary_json::jsonb,'{questionCount}','3'::jsonb)::text WHERE id=?",source.toString(),quiz);
        UUID id=start(); String base="/api/attempts/"+id;
        call(token,"PUT",base+"/answers/"+q,Map.of("selectedOptionIds",List.of(x)),200);
        call(token,"PUT",base+"/answers/"+q,Map.of("selectedOptionIds",List.of(a2)),400);
        call(token,"PUT",base+"/answers/"+q2,Map.of("selectedOptionIds",List.of(a2)),200);
        // The unselected TRUE_FALSE question remains a skip.
        call(token,"POST",base+"/complete",null,200);
        var response=call(token,"GET",base+"/result",null,200);
        var result=json.readTree(response.body());
        assertTrue(response.headers().firstValue("Cache-Control").orElse("").contains("no-store"));
        assertEquals(3,result.path("totalQuestions").asInt());assertEquals(1,result.path("score").asInt());
        assertEquals(1,result.path("correctCount").asInt());assertEquals(1,result.path("incorrectCount").asInt());
        assertEquals(1,result.path("skippedCount").asInt());assertEquals(33.33,result.path("scorePercent").asDouble());
        assertTrue(result.path("durationSeconds").asLong()>=0);assertTrue(result.has("completedAt"));
        var items=result.path("items");assertEquals(3,items.size());
        assertEquals(List.of("CORRECT","INCORRECT","SKIPPED"),List.of(items.get(0).path("outcome").asText(),items.get(1).path("outcome").asText(),items.get(2).path("outcome").asText()));
        assertEquals(q.toString(),items.get(0).path("question").path("id").asText());
        assertEquals(q2.toString(),items.get(1).path("question").path("id").asText());
        assertEquals(q3.toString(),items.get(2).path("question").path("id").asText());
        assertEquals(2,items.get(1).path("correctOptionIds").size());
        assertEquals(0,items.get(2).path("selectedOptionIds").size());
        assertFalse(response.body().contains("explanation"));assertFalse(response.body().contains("sourceId"));
        call(token,"GET",base+"/review",null,404);
    }
    @Test void twoUsersAndTwoAttemptsKeepIndependentHistoricalResults() throws Exception {
        UUID first=start();String base="/api/attempts/"+first;
        var active=call(token,"GET",base+"/result",null,409);
        assertFalse(active.body().contains("correctOptionIds"));assertFalse(active.body().contains("items"));
        call(null,"GET",base+"/result",null,401);
        call(token,"PUT",base+"/answers/"+q,Map.of("selectedOptionIds",List.of(x)),200);
        call(token,"POST",base+"/complete",null,200);
        var original=call(token,"GET",base+"/result",null,200);
        assertEquals(call(otherToken,"GET",base+"/result",null,404).body(),
                call(otherToken,"GET","/api/attempts/"+UUID.randomUUID()+"/result",null,404).body());
        call(token,"PATCH","/api/quizzes/"+quiz,Map.of("name","Renamed for repeat"),200);
        UUID second=start();assertNotEquals(first,second);
        call(token,"POST","/api/attempts/"+second+"/complete",null,200);
        var repeated=json.readTree(call(token,"GET","/api/attempts/"+second+"/result",null,200).body());
        assertEquals(0,repeated.path("score").asInt());assertEquals(1,repeated.path("skippedCount").asInt());
        assertEquals("Renamed for repeat",repeated.path("quiz").path("name").asText());
        call(token,"DELETE","/api/quizzes/"+quiz,null,204);
        assertEquals(original.body(),call(token,"GET",base+"/result",null,200).body());
        assertEquals("Snapshot",json.readTree(original.body()).path("quiz").path("name").asText());
    }
}
