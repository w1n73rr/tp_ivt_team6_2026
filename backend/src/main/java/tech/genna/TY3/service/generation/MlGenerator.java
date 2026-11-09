package tech.genna.TY3.service.generation;

import tech.genna.TY3.api.model.QuestionType;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;
import tech.genna.TY3.ml.GenerationEvent;
import tech.genna.TY3.ml.Quiz;

/** Boundary to the in-process ML package; prompt construction belongs to QuizGenerator. */
public interface MlGenerator {
    Result generate(UUID jobId, String text, GenerationParameters parameters);
    default Result generate(UUID jobId, String text, GenerationParameters parameters, Consumer<GenerationEvent> onProgress) {
        return generate(jobId, text, parameters);
    }
    record Result(UUID jobId, List<Question> questions, Quiz quiz) {
        public Result(UUID jobId, List<Question> questions) { this(jobId, questions, null); }
    }
    record Question(QuestionType type, String text, List<Option> options, String explanation) {}
    record Option(String text, boolean correct) {}
}
