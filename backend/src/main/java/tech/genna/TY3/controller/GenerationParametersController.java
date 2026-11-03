package tech.genna.TY3.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import tech.genna.TY3.api.GenerationApi;
import tech.genna.TY3.api.QuizzesApi;
import tech.genna.TY3.api.model.GenerateQuizRequest;
import tech.genna.TY3.api.model.GenerationJob;
import tech.genna.TY3.api.model.GenerationParametersResponse;
import tech.genna.TY3.service.generation.GenerationParametersService;
import java.util.UUID;
import java.util.Optional;
import org.springframework.web.context.request.NativeWebRequest;

@RestController
@RequiredArgsConstructor
public class GenerationParametersController implements GenerationApi, QuizzesApi {
    private final GenerationParametersService parameters;

    @Override
    public Optional<NativeWebRequest> getRequest() {
        return Optional.empty();
    }

    @Override
    public ResponseEntity<GenerationParametersResponse> getGenerationParameters() {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(parameters.getParameters());
    }

    @Override
    public ResponseEntity<GenerationJob> generateQuiz(UUID idempotencyKey, GenerateQuizRequest request) {
        parameters.validate(request);
        // Do not pretend to enqueue a job until the separate generation lifecycle is implemented.
        throw new ResponseStatusException(HttpStatus.NOT_IMPLEMENTED, "GENERATION_NOT_IMPLEMENTED");
    }
}
