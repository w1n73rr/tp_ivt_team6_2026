package tech.genna.TY3.service.generation;

import tech.genna.TY3.api.model.*;
import java.util.List;
import java.util.UUID;

/** The same server catalogue drives validation and the options endpoint. */
public record GenerationParameters(UUID materialId, String name, int questionCount,
                                   Difficulty difficulty, QuestionType questionType, String language) {
    public static final List<Integer> COUNTS = List.of(5, 10);
    public static final List<Difficulty> DIFFICULTIES = List.of(Difficulty.values());
    public static final List<QuestionType> TYPES = List.of(QuestionType.values());

    public static GenerationParameters from(GenerateQuizRequest request) {
        if (request == null || request.getMaterialId() == null || request.getQuestionCount() == null
                || !COUNTS.contains(request.getQuestionCount().getValue()) || request.getDifficulty() == null
                || !DIFFICULTIES.contains(request.getDifficulty()) || request.getQuestionType() == null
                || !TYPES.contains(request.getQuestionType())
                || request.getTopicIds() != null && !request.getTopicIds().isEmpty()
                || Boolean.TRUE.equals(request.getExcludeAnswered())
                || Boolean.TRUE.equals(request.getPrioritizeWeakTopics())
                || Boolean.TRUE.equals(request.getAdaptToLevel())) {
            throw new IllegalArgumentException("Unsupported generation parameters");
        }
        String name = request.getName() == null ? "Тест" : request.getName().strip();
        if (name.isBlank() || name.length() > 100) throw new IllegalArgumentException("Invalid test name");
        String language = request.getLanguage() == null ? "ru" : request.getLanguage().getValue();
        return new GenerationParameters(request.getMaterialId(), name, request.getQuestionCount().getValue(),
                request.getDifficulty(), request.getQuestionType(), language);
    }
}
