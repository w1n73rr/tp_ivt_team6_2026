package tech.genna.TY3.ml;

import org.junit.jupiter.api.Test;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.regex.Pattern;
import static org.junit.jupiter.api.Assertions.*;

class DocumentationExampleTest {
    @Test void documentedRequestAndResultStayValidAndMatchPublishedExamples() throws Exception {
        String requestJson;
        String resultJson;
        try (var request = getClass().getResourceAsStream("/ml/documentation-request.json");
             var result = getClass().getResourceAsStream("/ml/documentation-result.json")) {
            assertNotNull(request);
            assertNotNull(result);
            requestJson = new String(request.readAllBytes(), StandardCharsets.UTF_8);
            resultJson = new String(result.readAllBytes(), StandardCharsets.UTF_8);
        }
        var input = Json.read(requestJson);
        var material = (java.util.Map<?, ?>) input.get("material");
        var request = new GenerationRequest("demo-job", "demo-material", material.get("text").toString(),
                5, GenerationRequest.Difficulty.easy, GenerationRequest.QuestionType.single_choice);
        assertEquals(5, new QuizValidator().parse(resultJson, request).questions().size());
        var examples = Pattern.compile("```json\\s*(.*?)```", Pattern.DOTALL)
                .matcher(Files.readString(Path.of("ML.md")));
        assertTrue(examples.find());
        assertEquals(input, Json.read(examples.group(1)));
        assertTrue(examples.find());
        assertEquals(Json.read(resultJson), Json.read(examples.group(1)));
    }
}
