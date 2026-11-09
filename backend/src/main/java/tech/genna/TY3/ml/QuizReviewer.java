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
        Map<String, Object> input = new LinkedHashMap<>(Map.of("material", Map.of("text", request.text()),
                "difficulty", request.difficulty().name(), "quiz", quiz));
        for (int attempt = 0; attempt < 2; attempt++) {
            // Only a fully received, malformed verdict may be corrected. Transport failures
            // propagate without retry, because the provider may already have processed a call.
            String raw = client.complete(prompt, Json.write(input), () -> {});
            try { return parse(raw, quiz); }
            catch (GenerationFailure failure) {
                if (attempt == 1 || failure.code() != INVALID_MODEL_OUTPUT) throw failure;
                input.put("correction", Map.of("code", "INVALID_REVIEW_FORMAT", "instruction",
                        "Re-evaluate the original quiz against the source. Return ONE complete JSON verdict "
                        + "with exactly verdict and issues. A pass requires an empty issues array; a reject "
                        + "requires concrete issues with existing questionId and reason. No second object, "
                        + "Markdown or trailing text. Do not assume the quiz passes."));
            }
        }
        throw new GenerationFailure(INVALID_MODEL_OUTPUT);
    }

    private List<Map<String, String>> parse(String raw, Quiz quiz) {
        Map<?, ?> result = Json.read(raw);
        if (result == null || !result.keySet().equals(Set.of("verdict", "issues"))
                || !(result.get("issues") instanceof List<?> issues) || issues.size() > 12) {
            throw new GenerationFailure(INVALID_MODEL_OUTPUT, "REVIEW_FIELDS: expected verdict and issues array with at most 12 items.");
        }
        String verdict = Objects.toString(result.get("verdict"), "");
        if (!(verdict.equals("pass") && issues.isEmpty()
                || verdict.equals("reject") && !issues.isEmpty())) {
            throw new GenerationFailure(INVALID_MODEL_OUTPUT, "REVIEW_VERDICT: pass requires no issues; reject requires at least one issue.");
        }
        Set<String> ids = new HashSet<>();
        ids.add("quiz"); quiz.questions().forEach(q -> ids.add(q.id()));
        List<Map<String, String>> checked = new ArrayList<>();
        for (Object issue : issues) {
            if (!(issue instanceof Map<?, ?> item) || !item.keySet().equals(Set.of("questionId", "reason"))
                    || !(item.get("questionId") instanceof String id) || !ids.contains(id)
                    || !(item.get("reason") instanceof String reason) || reason.isBlank() || reason.length() > 1000) {
                throw new GenerationFailure(INVALID_MODEL_OUTPUT, "REVIEW_ISSUE: expected existing questionId and a nonempty reason up to 1000 characters.");
            }
            checked.add(Map.of("questionId", id, "reason", reason));
        }
        return List.copyOf(checked);
    }
}
