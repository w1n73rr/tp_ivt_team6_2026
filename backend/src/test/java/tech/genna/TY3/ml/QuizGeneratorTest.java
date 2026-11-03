package tech.genna.TY3.ml;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import static org.junit.jupiter.api.Assertions.*;
import static tech.genna.TY3.ml.GenerationEvent.Stage.*;
import static tech.genna.TY3.ml.GenerationRequest.*;

class QuizGeneratorTest {
    static final String REVIEW_PASS = "{\"verdict\":\"pass\",\"issues\":[]}";
    static boolean isReview(String system) { return system.startsWith("РОЛЬ ПРОВЕРЯЮЩЕГО"); }
    static GenerationRequest request(QuestionType type, int count) {
        return new GenerationRequest("job-1", "material-1", "Prepared lecture text", count, Difficulty.medium, type);
    }
    static Map<String, Object> output(QuestionType type, int count) {
        var questions = new ArrayList<Map<String, Object>>();
        for (int i = 1; i <= count; i++) {
            boolean tf = type == QuestionType.true_false;
            List<Map<String, String>> options = tf ? List.of(Map.of("id", "true", "text", "Верно"),
                    Map.of("id", "false", "text", "Неверно")) : List.of(
                    Map.of("id", "a", "text", "Option A"), Map.of("id", "b", "text", "Option B"),
                    Map.of("id", "c", "text", "Option C"), Map.of("id", "d", "text", "Option D"));
            questions.add(new LinkedHashMap<>(Map.of("id", "q" + i, "type", type.name(),
                    "text", "Question number " + i, "options", options,
                    "correctOptionIds", tf ? List.of(i % 2 == 0 ? "false" : "true") : type == QuestionType.multiple_choice
                            ? List.of("b", "c") : List.of("b"), "explanation", "From source")));
        }
        return new LinkedHashMap<>(Map.of("requestId", "job-1", "status", "success", "quiz",
                new LinkedHashMap<>(Map.of("schemaVersion", 2, "materialId", "material-1",
                        "title", "Test", "questions", questions))));
    }
    @SuppressWarnings("unchecked")
    static List<Map<String, Object>> questions(Map<String, Object> root) {
        return (List<Map<String, Object>>) ((Map<?, ?>) root.get("quiz")).get("questions");
    }

    @ParameterizedTest @EnumSource(QuestionType.class)
    void acceptsEachTypeAndReportsStagesWithoutClaimingDatabaseSuccess(QuestionType type) {
        for (int count : List.of(5, 10)) {
            var events = new ArrayList<GenerationEvent>();
            var generator = new QuizGenerator((system, user, first) -> {
                assertTrue(system.contains("РОЛЬ"));
                assertTrue(user.contains("Prepared lecture text"));
                if (isReview(system)) return REVIEW_PASS;
                first.run();
                return Json.write(output(type, count));
            }, 60000);
            var quiz = generator.generate(request(type, count), events::add);
            assertEquals(count, quiz.questions().size());
            assertEquals(List.of(PREPARING_PROMPT, WAITING_FOR_MODEL, RECEIVING_RESPONSE,
                    VALIDATING, REVIEWING_CONTENT, VALIDATED), events.stream().map(GenerationEvent::stage).toList());
            assertEquals(List.of(1L, 2L, 3L, 4L, 5L, 6L), events.stream().map(GenerationEvent::sequence).toList());
            assertTrue(events.stream().allMatch(e -> e.requestId().equals("job-1")));
            for (var q : quiz.questions()) {
                assertEquals(type, q.type());
                Set<String> correctTexts = new HashSet<>();
                q.options().stream().filter(o -> q.correctOptionIds().contains(o.id()))
                        .forEach(o -> correctTexts.add(o.text()));
                assertEquals(type == QuestionType.true_false ? Set.of(
                        Integer.parseInt(q.id().substring(1)) % 2 == 0 ? "Неверно" : "Верно")
                        : type == QuestionType.multiple_choice ? Set.of("Option B", "Option C")
                        : Set.of("Option B"), correctTexts);
            }
        }
    }

    @ParameterizedTest @ValueSource(ints = {0, 4, 6, 11})
    void invalidCountDoesNotCallModel(int count) {
        AtomicBoolean called = new AtomicBoolean();
        var g = new QuizGenerator((s, u, f) -> { called.set(true); return ""; }, 60000);
        assertEquals(GenerationFailure.Code.INVALID_INPUT, assertThrows(GenerationFailure.class,
                () -> g.generate(request(QuestionType.single_choice, count), e -> {})).code());
        assertFalse(called.get());
    }

    @Test void invalidOutputAndProviderRefusalAreFailures() {
        for (ModelClient client : List.<ModelClient>of((s,u,f) -> "not json",
                (s,u,f) -> { throw new GenerationFailure(GenerationFailure.Code.RATE_LIMITED); })) {
            var events = new ArrayList<GenerationEvent>();
            assertThrows(GenerationFailure.class, () -> new QuizGenerator(client, 60000)
                    .generate(request(QuestionType.single_choice, 5), events::add));
            assertEquals(FAILED, events.getLast().stage());
            assertFalse(events.stream().anyMatch(e -> e.stage() == VALIDATED));
        }
    }

    @Test void rejectsMissingFieldWrongTypeDuplicateOptionsWrongCountAndUnknownFields() {
        for (int variant = 0; variant < 7; variant++) {
            var root = output(QuestionType.single_choice, 5);
            var qs = questions(root);
            switch (variant) {
                case 0 -> qs.getFirst().remove("explanation");
                case 1 -> qs.getFirst().put("type", "multiple_choice");
                case 2 -> qs.getFirst().put("correctOptionIds", List.of("a", "b"));
                case 3 -> qs.removeLast();
                case 4 -> qs.getFirst().put("extra", "unexpected");
                case 5 -> qs.getFirst().put("options", Collections.nCopies(4, Map.of("id", "a", "text", "same")));
                case 6 -> root.put("requestId", "other-job");
            }
            assertEquals(GenerationFailure.Code.INVALID_MODEL_OUTPUT, assertThrows(GenerationFailure.class,
                    () -> new QuizValidator().parse(Json.write(root), request(QuestionType.single_choice, 5))).code());
        }
    }

    @Test void disconnectingListenerDoesNotRepeatOrInvalidateModelWork() {
        var g = new QuizGenerator((s,u,f) -> {
            if (isReview(s)) return REVIEW_PASS;
            f.run(); return Json.write(output(QuestionType.single_choice, 5));
        }, 60000);
        assertEquals(5, g.generate(request(QuestionType.single_choice, 5), e -> {
            throw new IllegalStateException("UI disconnected");
        }).questions().size());
    }

    @Test void insufficientMaterialHasSafeCode() {
        String response = Json.write(Map.of("requestId", "job-1", "status", "error", "error",
                Map.of("code", "INSUFFICIENT_MATERIAL", "message", "Not enough source facts")));
        assertEquals(GenerationFailure.Code.INSUFFICIENT_MATERIAL, assertThrows(GenerationFailure.class,
                () -> new QuizValidator().parse(response, request(QuestionType.single_choice, 5))).code());
    }

    @Test void rejectsDuplicateJsonKeysAndTrailingJsonObjects() {
        assertThrows(GenerationFailure.class, () -> Json.read("{\"status\":\"success\",\"status\":\"error\"}"));
        assertThrows(GenerationFailure.class, () -> Json.read("{} {}"));
    }

    @Test void rejectsTrueFalseWithOnlyOneAnswerValue() {
        for (String answer : List.of("true", "false")) {
            var root = output(QuestionType.true_false, 5);
            questions(root).forEach(q -> q.put("correctOptionIds", List.of(answer)));
            assertEquals(GenerationFailure.Code.INVALID_MODEL_OUTPUT, assertThrows(GenerationFailure.class,
                    () -> new QuizValidator().parse(Json.write(root), request(QuestionType.true_false, 5))).code());
        }
    }

    @Test void reviewDefectsTriggerOnlyOneCorrectionAndNeverReturnRejectedCandidate() {
        var calls = new java.util.concurrent.atomic.AtomicInteger();
        var generationCalls = new java.util.concurrent.atomic.AtomicInteger();
        var events = new ArrayList<GenerationEvent>();
        var g = new QuizGenerator((s,u,f) -> {
            calls.incrementAndGet();
            if (isReview(s)) return "{\"verdict\":\"reject\",\"issues\":[{\"questionId\":\"q3\",\"reason\":\"Semantic duplicates\"}]}";
            if (generationCalls.incrementAndGet() == 2) {
                assertTrue(u.contains("Semantic duplicates"));
                assertTrue(u.contains("previousQuiz"));
            }
            return Json.write(output(QuestionType.single_choice, 5));
        }, 60000);
        assertEquals(GenerationFailure.Code.QUALITY_REJECTED, assertThrows(GenerationFailure.class,
                () -> g.generate(request(QuestionType.single_choice, 5), events::add)).code());
        assertEquals(4, calls.get());
        assertEquals(2, generationCalls.get());
        assertTrue(events.stream().anyMatch(e -> e.stage() == REVISING));
        assertEquals(FAILED, events.getLast().stage());
    }

    @Test void correctedCandidateIsReviewedAgainBeforeSuccess() {
        var reviews = new java.util.concurrent.atomic.AtomicInteger();
        var g = new QuizGenerator((s,u,f) -> {
            if (!isReview(s)) return Json.write(output(QuestionType.single_choice, 5));
            return reviews.incrementAndGet() == 1
                    ? "{\"verdict\":\"reject\",\"issues\":[{\"questionId\":\"q1\",\"reason\":\"Wrong scope\"}]}"
                    : REVIEW_PASS;
        },60000);
        assertEquals(5, g.generate(request(QuestionType.single_choice,5),e->{}).questions().size());
        assertEquals(2,reviews.get());
    }

    @Test void transportErrorIsNotAutomaticallyRetried() {
        var calls = new java.util.concurrent.atomic.AtomicInteger();
        var g = new QuizGenerator((s,u,f) -> {
            calls.incrementAndGet(); throw new GenerationFailure(GenerationFailure.Code.MODEL_TIMEOUT);
        },60000);
        assertThrows(GenerationFailure.class,()->g.generate(request(QuestionType.single_choice,5),e->{}));
        assertEquals(1,calls.get());
    }

    @Test void totalDeadlineCoversEntireWorkflow() {
        var g = new QuizGenerator((s,u,f) -> {
            try { Thread.sleep(4000); } catch (InterruptedException ex) {
                Thread.currentThread().interrupt(); throw new GenerationFailure(GenerationFailure.Code.CANCELLED);
            }
            return REVIEW_PASS;
        },60000,java.time.Duration.ofMillis(100));
        assertTimeoutPreemptively(java.time.Duration.ofSeconds(2),()->assertEquals(GenerationFailure.Code.MODEL_TIMEOUT,
                assertThrows(GenerationFailure.class,()->g.generate(request(QuestionType.single_choice,5),e->{})).code()));
    }

    @Test void knownCompletedMalformedJsonCanBeCorrectedOnce() {
        var attempts = new java.util.concurrent.atomic.AtomicInteger();
        var g = new QuizGenerator((s,u,f)->{
            if(isReview(s))return REVIEW_PASS;
            if(attempts.incrementAndGet()==1)return "not json";
            assertTrue(u.contains("correction"));
            return Json.write(output(QuestionType.single_choice,5));
        },60000);
        assertEquals(5,g.generate(request(QuestionType.single_choice,5),e->{}).questions().size());
        assertEquals(2,attempts.get());
    }

    @Test void multipleChoiceCorrectionContainsExactAnswerCountDefect() {
        var attempts = new java.util.concurrent.atomic.AtomicInteger();
        var generator = new QuizGenerator((system, user, first) -> {
            if (isReview(system)) return REVIEW_PASS;
            var root = output(QuestionType.multiple_choice, 5);
            if (attempts.incrementAndGet() == 1) {
                questions(root).getFirst().put("correctOptionIds", List.of("b"));
            } else {
                assertTrue(user.contains("questions[0].correctOptionIds"));
                assertTrue(user.contains("2 или 3"));
            }
            return Json.write(root);
        }, 60000);
        assertEquals(5, generator.generate(request(QuestionType.multiple_choice, 5), event -> {}).questions().size());
        assertEquals(2, attempts.get());
    }

    @ParameterizedTest @EnumSource(QuestionType.class)
    void promptContainsOnlySelectedTypeRulesAndMatchingExample(QuestionType type) {
        var generator = new QuizGenerator((system, user, first) -> {
            if (isReview(system)) return REVIEW_PASS;
            assertFalse(system.contains("{{TYPE_RULES}}"));
            assertFalse(system.contains("{{EXAMPLE}}"));
            for (var other : QuestionType.values()) {
                if (other != type) assertFalse(system.contains(other.name()), "Other type rules confuse the request");
            }
            return Json.write(output(type, 5));
        }, 60000);
        assertEquals(5, generator.generate(request(type, 5), event -> {}).questions().size());
    }
}
