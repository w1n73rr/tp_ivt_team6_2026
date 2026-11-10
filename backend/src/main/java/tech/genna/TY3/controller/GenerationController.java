package tech.genna.TY3.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;
import tech.genna.TY3.api.GenerationApi;
import tech.genna.TY3.api.QuizzesApi;
import tech.genna.TY3.api.model.*;
import tech.genna.TY3.security.CurrentUser;
import tech.genna.TY3.service.generation.*;
import java.net.URI;
import java.util.UUID;

@RestController
@RequiredArgsConstructor
public class GenerationController implements QuizzesApi, GenerationApi {
    private final GenerationStore store;
    private final CurrentUser currentUser;
    private final GenerationParametersService parameters;

    @Override public java.util.Optional<org.springframework.web.context.request.NativeWebRequest> getRequest() {
        return java.util.Optional.empty();
    }

    @Override public ResponseEntity<GenerationJob> generateQuiz(UUID idempotencyKey, GenerateQuizRequest request) {
        var user = currentUser.get();
        if (idempotencyKey == null) throw new IllegalArgumentException("Idempotency-Key required");
        var job = store.enqueue(idempotencyKey, GenerationParameters.from(request), user);
        return ResponseEntity.accepted().location(URI.create("/api/generation-jobs/" + job.getId())).body(job);
    }
    @Override public ResponseEntity<GenerationJob> getGenerationJob(UUID jobId) {
        return ResponseEntity.ok(store.get(jobId, currentUser.get()));
    }
    @Override public ResponseEntity<GenerationOptions> getGenerationOptions() {
        currentUser.get();
        var catalog = parameters.getParameters();
        var defaults = catalog.getDefaults();
        return ResponseEntity.ok(new GenerationOptions(
                catalog.getQuestionCounts().stream().filter(QuestionCountOption::getAvailable)
                        .map(o -> o.getValue().getValue()).toList(),
                catalog.getDifficulties().stream().filter(DifficultyOption::getAvailable).map(DifficultyOption::getValue).toList(),
                catalog.getQuestionTypes().stream().filter(QuestionTypeOption::getAvailable).map(QuestionTypeOption::getValue).toList(),
                defaults.getQuestionCount().getValue(), defaults.getDifficulty(), defaults.getQuestionType()));
    }
}
