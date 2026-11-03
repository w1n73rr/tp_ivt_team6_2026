package tech.genna.TY3.ml;

/** Prepared text only. File extraction, authorization and job persistence belong to BE. */
public record GenerationRequest(String requestId, String materialId, String text,
                                int questionCount, Difficulty difficulty, QuestionType questionType) {
    public enum Difficulty { easy, medium, hard }
    public enum QuestionType { single_choice, multiple_choice, true_false }

    public void validate(int maxChars) {
        if (requestId == null || requestId.isBlank() || requestId.length() > 100
                || materialId == null || materialId.isBlank() || materialId.length() > 100
                || text == null || text.isBlank() || text.length() > maxChars
                || (questionCount != 5 && questionCount != 10)
                || difficulty == null || questionType == null) {
            throw new GenerationFailure(GenerationFailure.Code.INVALID_INPUT);
        }
    }
}
