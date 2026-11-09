package tech.genna.TY3.ml;

import java.text.Normalizer;
import java.util.*;
import static tech.genna.TY3.ml.GenerationFailure.Code.*;

/** Formal M1 v2 validation plus request-dependent invariants. Does not prove factual correctness. */
public final class QuizValidator {
    public Quiz parse(String raw, GenerationRequest request) {
        try { return parseObject(Json.read(raw), request); }
        catch (GenerationFailure ex) { throw ex; }
        catch (RuntimeException ex) { throw new GenerationFailure(INVALID_MODEL_OUTPUT); }
    }

    private Quiz parseObject(Map<?, ?> root, GenerationRequest r) {
        check(r.requestId().equals(root.get("requestId")), "REQUEST_ID: must equal input requestId.");
        if ("error".equals(root.get("status"))) {
            keys(root, "requestId", "status", "error");
            Map<?, ?> error = object(root.get("error"));
            keys(error, "code", "message");
            text(error.get("message"));
            check("INSUFFICIENT_MATERIAL".equals(error.get("code")));
            throw new GenerationFailure(INSUFFICIENT_MATERIAL);
        }
        keys(root, "requestId", "status", "quiz");
        check("success".equals(root.get("status")));
        Map<?, ?> quiz = object(root.get("quiz"));
        keys(quiz, "schemaVersion", "materialId", "title", "questions");
        check(Integer.valueOf(2).equals(quiz.get("schemaVersion")), "SCHEMA_VERSION: expected integer 2.");
        check(r.materialId().equals(quiz.get("materialId")), "MATERIAL_ID: must equal input material.id.");
        List<?> questions = array(quiz.get("questions"));
        check(questions.size() == r.questionCount(), "questions: expected exactly " + r.questionCount() + " questions.");
        List<Quiz.Question> result = new ArrayList<>();
        Set<String> questionTexts = new HashSet<>();
        for (int i = 0; i < questions.size(); i++) {
            Map<?, ?> q = object(questions.get(i));
            keys(q, "id", "type", "text", "options", "correctOptionIds", "explanation");
            check(("q" + (i + 1)).equals(q.get("id")), "QUESTION_ID: expected sequential q1..qN.");
            check(r.questionType().name().equals(q.get("type")), "questions[" + i + "].type: expected " + r.questionType().name());
            String questionText = text(q.get("text"));
            check(questionTexts.add(normalize(questionText)), "DUPLICATE_QUESTION: question texts must be distinct.");
            boolean tf = r.questionType() == GenerationRequest.QuestionType.true_false;
            List<String> ids = tf ? List.of("true", "false") : List.of("a", "b", "c", "d");
            List<?> options = array(q.get("options"));
            check(options.size() == ids.size(), "OPTIONS_COUNT: expected " + ids.size() + " options.");
            Set<String> optionIds = new HashSet<>(), optionTexts = new HashSet<>();
            List<Quiz.Option> converted = new ArrayList<>();
            for (Object value : options) {
                Map<?, ?> option = object(value);
                keys(option, "id", "text");
                String id = text(option.get("id")), t = text(option.get("text"));
                check(ids.contains(id) && optionIds.add(id) && optionTexts.add(normalize(t)));
                if (tf) check(t.equals(id.equals("true") ? "Верно" : "Неверно"));
                converted.add(new Quiz.Option(id, t));
            }
            List<?> answers = array(q.get("correctOptionIds"));
            boolean multi = r.questionType() == GenerationRequest.QuestionType.multiple_choice;
            check(multi ? answers.size() >= 2 && answers.size() <= 3 : answers.size() == 1,
                    "questions[" + i + "].correctOptionIds: " + (multi
                            ? "expected 2 or 3 distinct correct answers; one is invalid."
                            : "expected exactly one correct answer."));
            List<String> correct = answers.stream().map(QuizValidator::text).toList();
            check(new HashSet<>(correct).size() == correct.size() && optionIds.containsAll(correct));
            result.add(new Quiz.Question("q" + (i + 1), r.questionType(), questionText,
                    converted, correct, text(q.get("explanation"))));
        }
        if (r.questionType() == GenerationRequest.QuestionType.true_false) {
            long trueAnswers = result.stream().filter(q -> q.correctOptionIds().contains("true")).count();
            check(trueAnswers > 0 && trueAnswers < result.size(), "A true_false quiz must contain both true and false answers.");
        }
        return new Quiz(2, r.materialId(), text(quiz.get("title")), result);
    }
    private static String normalize(String s) {
        return Normalizer.normalize(s, Normalizer.Form.NFKC).toLowerCase(Locale.ROOT)
                .replaceAll("[\\p{P}\\s]+", " ").strip();
    }
    private static void keys(Map<?, ?> m, String... names) { check(m.keySet().equals(Set.of(names)), "OBJECT_FIELDS: expected exactly " + String.join(",", names)); }
    private static Map<?, ?> object(Object o) { check(o instanceof Map); return (Map<?, ?>) o; }
    private static List<?> array(Object o) { check(o instanceof List); return (List<?>) o; }
    private static String text(Object o) {
        check(o instanceof String);
        String s = (String) o;
        check(!s.isBlank() && s.length() <= 4000);
        return s;
    }
    private static void check(boolean ok) { if (!ok) throw new GenerationFailure(INVALID_MODEL_OUTPUT); }
    private static void check(boolean ok, String hint) {
        if (!ok) throw new GenerationFailure(INVALID_MODEL_OUTPUT, hint);
    }
}
