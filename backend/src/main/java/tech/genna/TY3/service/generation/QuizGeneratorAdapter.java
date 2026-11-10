package tech.genna.TY3.service.generation;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import tech.genna.TY3.api.model.QuestionType;
import tech.genna.TY3.ml.GenerationEvent;
import tech.genna.TY3.ml.GenerationRequest;
import tech.genna.TY3.ml.QuizGenerator;
import java.util.UUID;
import java.util.function.Consumer;

@Component
@RequiredArgsConstructor
public class QuizGeneratorAdapter implements MlGenerator {
    private final QuizGenerator generator;

    @Override public Result generate(UUID jobId, String text, GenerationParameters parameters) {
        return generate(jobId, text, parameters, event -> { });
    }

    @Override public Result generate(UUID jobId, String text, GenerationParameters parameters,
                                    Consumer<GenerationEvent> onProgress) {
        var request = new GenerationRequest(jobId.toString(), parameters.materialId().toString(), text,
                parameters.questionCount(), switch (parameters.difficulty()) {
                    case EASY -> GenerationRequest.Difficulty.easy;
                    case MEDIUM -> GenerationRequest.Difficulty.medium;
                    case HARD -> GenerationRequest.Difficulty.hard;
                }, switch (parameters.questionType()) {
                    case SINGLE_CHOICE -> GenerationRequest.QuestionType.single_choice;
                    case MULTIPLE_CHOICE -> GenerationRequest.QuestionType.multiple_choice;
                    case TRUE_FALSE -> GenerationRequest.QuestionType.true_false;
                });
        try {
            var quiz = generator.generate(request, onProgress);
            if (quiz.schemaVersion() != 2 || !request.materialId().equals(quiz.materialId()))
                throw new GenerationFailure("ML_INVALID_RESULT");
            var questions = quiz.questions().stream().map(q -> new Question(switch (q.type()) {
                case single_choice -> QuestionType.SINGLE_CHOICE;
                case multiple_choice -> QuestionType.MULTIPLE_CHOICE;
                case true_false -> QuestionType.TRUE_FALSE;
            }, q.text(), q.options().stream().map(option -> new Option(option.text(),
                    q.correctOptionIds().contains(option.id()))).toList(), q.explanation())).toList();
            return new Result(jobId, questions, quiz);
        } catch (tech.genna.TY3.ml.GenerationFailure failure) {
            throw new GenerationFailure(failure.code().name());
        }
    }
}
