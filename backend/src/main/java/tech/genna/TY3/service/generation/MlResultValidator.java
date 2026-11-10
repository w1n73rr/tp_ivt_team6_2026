package tech.genna.TY3.service.generation;

import org.springframework.stereotype.Component;
import tech.genna.TY3.api.model.QuestionType;
import java.util.HashSet;
import java.util.UUID;

@Component
public class MlResultValidator {
    public void validate(UUID jobId, GenerationParameters parameters, MlGenerator.Result result) {
        if (result == null || !jobId.equals(result.jobId()) || result.questions() == null
                || result.questions().size() != parameters.questionCount()) invalid();
        var texts = new HashSet<String>();
        for (var q : result.questions()) {
            if (q == null || q.type() != parameters.questionType() || !text(q.text(), 4000)
                    || !texts.add(q.text().strip()) || q.options() == null
                    || q.options().size() != (q.type() == QuestionType.TRUE_FALSE ? 2 : 4)
                    || q.explanation() != null && q.explanation().length() > 8000) invalid();
            var options = new HashSet<String>();
            int correct = 0;
            for (var option : q.options()) {
                if (option == null || !text(option.text(), 4000) || !options.add(option.text().strip())) invalid();
                if (option.correct()) correct++;
            }
            if (q.type() == QuestionType.MULTIPLE_CHOICE ? correct < 2 || correct >= q.options().size() : correct != 1)
                invalid();
        }
    }
    private boolean text(String text, int limit) { return text != null && !text.isBlank() && text.length() <= limit; }
    private void invalid() { throw new GenerationFailure("ML_INVALID_RESULT"); }
}
