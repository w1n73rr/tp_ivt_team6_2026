package tech.genna.TY3.service.generation;

import tech.genna.TY3.api.model.Difficulty;
import tech.genna.TY3.api.model.QuestionType;

import java.util.Locale;

/** Immutable values to pass to the job/ML boundary, independent of the live catalog. */
public record SelectedGenerationSettings(int questionCount, Difficulty difficulty, QuestionType questionType) {
    public String mlDifficulty() {
        return difficulty.getValue().toLowerCase(Locale.ROOT);
    }

    public String mlQuestionType() {
        return questionType.getValue().toLowerCase(Locale.ROOT);
    }
}
