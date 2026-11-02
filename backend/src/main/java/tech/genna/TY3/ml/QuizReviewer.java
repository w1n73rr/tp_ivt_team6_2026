package tech.genna.TY3.ml;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.*;
import static tech.genna.TY3.ml.GenerationFailure.Code.INVALID_MODEL_OUTPUT;

/** Model-assisted quality gate, not a mathematical proof or an independent human review. */
final class QuizReviewer {
    private final ModelClient client;
    private final String prompt;

    QuizReviewer(ModelClient client) {
        this.client = client;
        try (var input = getClass().getResourceAsStream("/ml/review-prompt.txt")) {
            if (input == null) throw new IllegalStateException("Missing review prompt");
            prompt = new String(input.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException ex) { throw new IllegalStateException("Cannot load review prompt"); }
    }

    List<Map<String, String>> review(GenerationRequest request, Quiz quiz) {
        String user = Json.write(Map.of("material", Map.of("text", request.text()),
                "difficulty", request.difficulty().name(), "quiz", quiz));
        Map<?, ?> result = Json.read(client.complete(prompt, user, () -> {}));
        if (result == null || !result.keySet().equals(Set.of("verdict", "issues"))
                || !(result.get("issues") instanceof List<?> issues) || issues.size() > 12) {
            throw new GenerationFailure(INVALID_MODEL_OUTPUT);
        }
        String verdict = Objects.toString(result.get("verdict"), "");
        if (!(verdict.equals("pass") && issues.isEmpty()
                || verdict.equals("reject") && !issues.isEmpty())) {
            throw new GenerationFailure(INVALID_MODEL_OUTPUT);
        }
        Set<String> ids = new HashSet<>();
        ids.add("quiz"); quiz.questions().forEach(q -> ids.add(q.id()));
        List<Map<String, String>> checked = new ArrayList<>();
        for (Object issue : issues) {
            if (!(issue instanceof Map<?, ?> item) || !item.keySet().equals(Set.of("questionId", "reason"))
                    || !(item.get("questionId") instanceof String id) || !ids.contains(id)
                    || !(item.get("reason") instanceof String reason) || reason.isBlank() || reason.length() > 1000) {
                throw new GenerationFailure(INVALID_MODEL_OUTPUT);
            }
            checked.add(Map.of("questionId", id, "reason", reason));
        }
        return List.copyOf(checked);
    }
}
