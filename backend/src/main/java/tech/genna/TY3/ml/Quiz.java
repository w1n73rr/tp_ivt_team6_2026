package tech.genna.TY3.ml;

import java.util.List;

/** M1 v2: ML output contract selected by PO; independent of database IDs and FE transport DTOs. */
public record Quiz(int schemaVersion, String materialId, String title, List<Question> questions) {
    public Quiz { questions = List.copyOf(questions); }
    public record Question(String id, GenerationRequest.QuestionType type, String text,
                           List<Option> options, List<String> correctOptionIds, String explanation) {
        public Question { options = List.copyOf(options); correctOptionIds = List.copyOf(correctOptionIds); }
    }
    public record Option(String id, String text) {}
}
