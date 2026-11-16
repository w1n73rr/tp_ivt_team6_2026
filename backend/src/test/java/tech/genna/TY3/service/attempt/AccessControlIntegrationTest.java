package tech.genna.TY3.service.attempt;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.AfterEach;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

/** Uses the same isolated real HTTP/PostgreSQL fixtures; inherited scenarios are intentionally rerun. */
class AccessControlIntegrationTest extends AttemptIntegrationTest {
    @AfterEach void removeMaterialFixture() {
        if(owner!=null) {
            jdbc.update("DELETE FROM materials WHERE user_id=?",owner);
            jdbc.update("DELETE FROM subjects WHERE user_id=?",owner);
        }
    }
    @Test void anonymousRequestsAreRejectedBeforeObjectLookupOrBodyParsing() throws Exception {
        String object=UUID.randomUUID().toString();
        for(String route:List.of(
                "GET /api/materials", "POST /api/materials", "GET /api/materials/"+object,
                "PATCH /api/materials/"+object,"DELETE /api/materials/"+object,
                "GET /api/materials/"+object+"/file","GET /api/materials/"+object+"/download",
                "GET /api/files/"+object,"DELETE /api/files/"+object,"POST /api/files",
                "GET /api/quizzes","POST /api/quizzes","GET /api/quizzes/"+quiz,
                "PATCH /api/quizzes/"+quiz,"DELETE /api/quizzes/"+quiz,
                "GET /api/generation-jobs/"+job,"POST /api/quizzes/"+quiz+"/attempts",
                "GET /api/attempts","GET /api/attempts/"+object,
                "PUT /api/attempts/"+object+"/answers/"+q,
                "POST /api/attempts/"+object+"/complete","GET /api/attempts/"+object+"/result")) {
            String[] parts=route.split(" ",2);
            var response=call(null,parts[0],parts[1],null,401);
            assertFalse(response.body().contains("Snapshot"));
        }
    }
    @Test void otherOwnerAndMissingObjectAreIndistinguishable() throws Exception {
        UUID attempt=start();
        UUID material=UUID.randomUUID(),subject=UUID.randomUUID();
        jdbc.update("INSERT INTO subjects(id,user_id,name) VALUES (?,?,'Private subject')",subject,owner);
        jdbc.update("INSERT INTO materials(id,user_id,subject_id,title,file_id,original_filename,file_type,size_bytes,status,created_at) VALUES (?,?,?,'Private material',?,'private.txt','TXT',10,'STORED',CURRENT_TIMESTAMP)",
                material,owner,subject,UUID.randomUUID());
        for(String method:List.of("GET","DELETE","PATCH")) {
            Object body=method.equals("PATCH")?Map.of("name","New"):null;
            assertEquals(call(otherToken,method,"/api/quizzes/"+quiz,body,404).body(),
                    call(otherToken,method,"/api/quizzes/"+UUID.randomUUID(),body,404).body());
        }
        assertEquals(call(otherToken,"GET","/api/generation-jobs/"+job,null,404).body(),
                call(otherToken,"GET","/api/generation-jobs/"+UUID.randomUUID(),null,404).body());
        assertEquals(call(otherToken,"GET","/api/attempts/"+attempt,null,404).body(),
                call(otherToken,"GET","/api/attempts/"+UUID.randomUUID(),null,404).body());
        assertEquals(call(otherToken,"POST","/api/quizzes/"+quiz+"/attempts",null,404).body(),
                call(otherToken,"POST","/api/quizzes/"+UUID.randomUUID()+"/attempts",null,404).body());
        for(String suffix:List.of("","/file","/download")) {
            assertEquals(call(otherToken,"GET","/api/materials/"+material+suffix,null,404).body(),
                    call(otherToken,"GET","/api/materials/"+UUID.randomUUID()+suffix,null,404).body());
        }
        for(String method:List.of("PATCH","DELETE")) {
            Object body=method.equals("PATCH")?Map.of("name","New","subjectName","Private subject"):null;
            assertEquals(call(otherToken,method,"/api/materials/"+material,body,404).body(),
                    call(otherToken,method,"/api/materials/"+UUID.randomUUID(),body,404).body());
        }
        for(String path:List.of("/api/quizzes","/api/quizzes/"+quiz,"/api/attempts/"+attempt)) {
            var response=call(token,"GET",path,null,200);
            assertFalse(response.body().contains("correctOptionIds"));
            assertFalse(response.body().contains("scorePercent\":0"));
            assertTrue(response.headers().firstValue("Cache-Control").orElse("").contains("no-store"));
        }
    }
    @Test void omittedSelectionCannotAccidentallyClearAnAnswer() throws Exception {
        UUID attempt=start(); String path="/api/attempts/"+attempt+"/answers/"+q;
        call(token,"PUT",path,Map.of("selectedOptionIds",List.of(x)),200);
        for(Object invalid:List.of(Map.of(),Map.of("selectedOptionIds",List.of(x,x)),
                Map.of("selectedOptionIds",List.of(),"score",100),Map.of("selectedOptionIds",List.of(),"timeSpentSeconds",1.5))) {
            call(token,"PUT",path,invalid,400);
        }
        assertEquals(x.toString(),json.readTree(call(token,"GET","/api/attempts/"+attempt,null,200).body())
                .path("answers").get(0).path("selectedOptionIds").get(0).asText());
    }
}
