package tech.genna.TY3.service.attempt;

import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import tech.genna.TY3.tests.base.BaseTest;
import tech.genna.TY3.api.model.*;
import tools.jackson.databind.*;
import java.net.URI;
import java.net.http.*;
import java.time.Duration;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

/** Real HTTP/JWT and PostgreSQL. Only source quizzes are controlled fixtures; attempts use production APIs. */
class AttemptIntegrationTest extends BaseTest {
    @Autowired JdbcTemplate jdbc;
    @Autowired ObjectMapper json;
    UUID owner, other, quiz, job;
    String token, otherToken;
    UUID q=UUID.randomUUID(), x=UUID.randomUUID(), y=UUID.randomUUID();
    @BeforeEach void fixture() throws Exception {
        String name="attempt_"+UUID.randomUUID().toString().replace("-","");
        token=register(name); otherToken=register(name+"b");
        owner=jdbc.queryForObject("SELECT id FROM users WHERE username=?",UUID.class,name);
        other=jdbc.queryForObject("SELECT id FROM users WHERE username=?",UUID.class,name+"b");
        quiz=UUID.randomUUID();job=UUID.randomUUID(); UUID material=UUID.randomUUID();
        var summary=new QuizSummary().id(quiz).name("Snapshot").questionCount(1).materialId(material)
                .difficulty(Difficulty.MEDIUM).questionType(QuestionType.SINGLE_CHOICE);
        var result=json.createObjectNode();
        var question=result.putArray("questions").addObject().put("id",q.toString()).put("type","single_choice").put("text","Q");
        question.putArray("options").addObject().put("id",x.toString()).put("text","A");
        ((tools.jackson.databind.node.ArrayNode)question.get("options")).addObject().put("id",y.toString()).put("text","B");
        question.putArray("correctOptionIds").add(x.toString());
        jdbc.update("INSERT INTO generation_jobs(id,user_id,request_json,material_id,file_id,format,material_name,subject_id,subject_name,status) VALUES (?,?,?,?,?,'TXT','M',?,'S','COMPLETED')",
                job,owner,"{\"materialId\":\""+material+"\",\"questionCount\":5,\"difficulty\":\"MEDIUM\",\"questionType\":\"SINGLE_CHOICE\"}",material,UUID.randomUUID(),UUID.randomUUID());
        jdbc.update("INSERT INTO quizzes(id,job_id,user_id,summary_json,result_json) VALUES (?,?,?,?,?)",quiz,job,owner,json.writeValueAsString(summary),result.toString());
    }
    @AfterEach void cleanup() {
        if(owner!=null) {
            jdbc.update("DELETE FROM attempts WHERE user_id IN (?,?)",owner,other);
            jdbc.update("DELETE FROM quizzes WHERE user_id=?",owner);
            jdbc.update("DELETE FROM generation_jobs WHERE user_id=?",owner);
            jdbc.update("DELETE FROM users WHERE id IN (?,?)",owner,other);
        }
    }
    String register(String name) throws Exception {
        return json.readTree(call(null,"POST","/api/auth/register",Map.of("username",name,"email",name+"@example.test","password","integration-only-password"),200).body()).path("token").asText();
    }
    HttpResponse<String> call(String auth,String method,String path,Object body,int expected) throws Exception {
        return call(auth,method,path,body,expected,UUID.randomUUID());
    }
    HttpResponse<String> call(String auth,String method,String path,Object body,int expected,UUID key) throws Exception {
        var builder=HttpRequest.newBuilder(URI.create("http://127.0.0.1:"+port+path)).timeout(Duration.ofSeconds(20));
        if(auth!=null) builder.header("Authorization","Bearer "+auth);
        builder.header("Idempotency-Key",key.toString());
        if(body!=null) builder.header("Content-Type","application/json");
        builder.method(method,body==null?HttpRequest.BodyPublishers.noBody():HttpRequest.BodyPublishers.ofString(json.writeValueAsString(body)));
        try(var client=HttpClient.newHttpClient()) {
            var response=client.send(builder.build(),HttpResponse.BodyHandlers.ofString());
            if(expected>0) assertEquals(expected,response.statusCode(),"Unexpected status for "+method+" "+path);
            return response;
        }
    }
    UUID start() throws Exception {
        var response=call(token,"POST","/api/quizzes/"+quiz+"/attempts",null,201);
        assertTrue(response.headers().firstValue("Cache-Control").orElse("").contains("no-store"));
        assertFalse(response.body().contains("correctOptionIds"));
        return UUID.fromString(json.readTree(response.body()).path("attempt").path("id").asText());
    }
    @Test void replaceClearReplayAndOwnerIsolation() throws Exception {
        UUID id=start(); String answer="/api/attempts/"+id+"/answers/"+q;
        var first=call(token,"PUT",answer,Map.of("selectedOptionIds",List.of(x)),200);
        assertEquals(first.body(),call(token,"PUT",answer,Map.of("selectedOptionIds",List.of(x)),200).body());
        call(token,"PUT",answer,Map.of("selectedOptionIds",List.of(y)),200);
        call(token,"PUT",answer,Map.of("selectedOptionIds",List.of(UUID.randomUUID())),400);
        var stored=json.readTree(call(token,"GET","/api/attempts/"+id,null,200).body());
        assertEquals(y.toString(),stored.path("answers").get(0).path("selectedOptionIds").get(0).asText());
        call(token,"PUT",answer,Map.of("selectedOptionIds",List.of()),200);
        assertEquals(0,json.readTree(call(token,"GET","/api/attempts/"+id,null,200).body()).path("attempt").path("answeredCount").asInt());
        call(null,"PUT",answer,Map.of("selectedOptionIds",List.of(x)),401);
        var foreign=call(otherToken,"PUT",answer,Map.of("selectedOptionIds",List.of(x)),404);
        var missing=call(otherToken,"PUT","/api/attempts/"+UUID.randomUUID()+"/answers/"+q,Map.of("selectedOptionIds",List.of(x)),404);
        assertEquals(foreign.body(),missing.body());
        jdbc.update("UPDATE attempts SET status='ABANDONED' WHERE id=?",id);
        call(token,"PUT",answer,Map.of("selectedOptionIds",List.of(x)),409);
    }
    @Test void startIsIdempotentAndSnapshotsSurviveQuizDeletion() throws Exception {
        UUID key=UUID.randomUUID();String path="/api/quizzes/"+quiz+"/attempts";
        String first=call(token,"POST",path,null,201,key).body();
        assertEquals(first,call(token,"POST",path,null,201,key).body());
        UUID id=UUID.fromString(json.readTree(first).path("attempt").path("id").asText());
        assertNotEquals(id,start());
        jdbc.update("DELETE FROM quizzes WHERE id=?",quiz);
        call(token,"GET","/api/attempts/"+id,null,200);
        call(otherToken,"GET","/api/attempts/"+id,null,404);
    }
}
