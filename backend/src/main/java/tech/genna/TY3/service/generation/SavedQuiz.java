package tech.genna.TY3.service.generation;

import java.util.*;

/** Private persisted questions: ML-local IDs are mapped to fresh server UUIDs with answer links preserved. */
record SavedQuiz(int schemaVersion, UUID materialId, String title, List<Question> questions) {
    record Question(UUID id, String sourceId, String type, String text, List<Option> options,
                    List<UUID> correctOptionIds, String explanation) {}
    record Option(UUID id, String sourceId, String text) {}

    static SavedQuiz from(MlGenerator.Result result, GenerationParameters parameters) {
        if (result.quiz() == null) throw new GenerationFailure("ML_INVALID_RESULT");
        final tech.genna.TY3.ml.Quiz quiz;
        try {
            quiz = new tech.genna.TY3.ml.QuizValidator().validate(result.quiz(), new tech.genna.TY3.ml.GenerationRequest(
                    result.jobId().toString(), parameters.materialId().toString(), "Validated result", parameters.questionCount(),
                    tech.genna.TY3.ml.GenerationRequest.Difficulty.valueOf(parameters.difficulty().name().toLowerCase(Locale.ROOT)),
                    tech.genna.TY3.ml.GenerationRequest.QuestionType.valueOf(parameters.questionType().name().toLowerCase(Locale.ROOT))));
        } catch (tech.genna.TY3.ml.GenerationFailure failure) {
            throw new GenerationFailure("ML_INVALID_RESULT");
        }
        var questions = quiz.questions().stream().map(q -> {
            Map<String, UUID> ids = new HashMap<>();
            var options = q.options().stream().map(option -> {
                UUID id = UUID.randomUUID();
                if (ids.put(option.id(), id) != null) throw new GenerationFailure("ML_INVALID_RESULT");
                return new Option(id, option.id(), option.text());
            }).toList();
            var correct = q.correctOptionIds().stream().map(id -> {
                UUID mapped = ids.get(id);
                if (mapped == null) throw new GenerationFailure("ML_INVALID_RESULT");
                return mapped;
            }).toList();
            return new Question(UUID.randomUUID(), q.id(), q.type().name(), q.text(), options, correct, q.explanation());
        }).toList();
        return new SavedQuiz(quiz.schemaVersion(), parameters.materialId(), quiz.title(), questions);
    }
}
