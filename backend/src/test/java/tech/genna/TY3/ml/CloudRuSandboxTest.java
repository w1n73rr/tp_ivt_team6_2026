package tech.genna.TY3.ml;

import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import java.nio.file.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

/** Exports the actual generator request for manual Cloud.ru playground comparison. */
@EnabledIfEnvironmentVariable(named = "ML_EXPORT_SANDBOX", matches = "true")
class CloudRuSandboxTest {
    @ParameterizedTest @EnumSource(GenerationRequest.QuestionType.class)
    void exportsCurrentPromptWithoutCallingProvider(GenerationRequest.QuestionType type) throws Exception {
        Path folder = Path.of("build", "cloudru-sandbox");
        Files.createDirectories(folder);
        String material = Files.readString(Path.of("..", "tests", "ui", "src", "test", "resources", "generation", "lecture.txt"));
        ModelClient capture = (system, user, first) -> {
            try {
                Files.writeString(folder.resolve(type + "-system.txt"), system);
                Files.writeString(folder.resolve(type + "-user.json"), user);
                Files.writeString(folder.resolve(type + "-request.json"), Json.write(Map.of(
                        "model", "deepseek-ai/DeepSeek-V4-Flash", "stream", true,
                        "temperature", 0.2, "max_tokens", 4000,
                        "response_format", Map.of("type", "json_object"),
                        "messages", List.of(Map.of("role", "system", "content", system),
                                Map.of("role", "user", "content", user)))));
            } catch (java.io.IOException ex) { throw new IllegalStateException(ex); }
            throw new GenerationFailure(GenerationFailure.Code.CANCELLED);
        };
        var request = new GenerationRequest("cloudru-" + type, "qa-lecture", material,
                5, GenerationRequest.Difficulty.medium, type);
        assertEquals(GenerationFailure.Code.CANCELLED, assertThrows(GenerationFailure.class,
                () -> new QuizGenerator(capture, 60000).generate(request, event -> {})).code());
        assertTrue(Files.size(folder.resolve(type + "-request.json")) > 1000);
    }
}
