package tech.genna.TY3.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;
import tech.genna.TY3.api.GenerationParametersApi;
import tech.genna.TY3.api.model.GenerationParametersResponse;
import tech.genna.TY3.service.generation.GenerationParametersService;
import java.util.Optional;
import org.springframework.web.context.request.NativeWebRequest;

@RestController
@RequiredArgsConstructor
public class GenerationParametersController implements GenerationParametersApi {
    private final GenerationParametersService parameters;

    @Override
    public Optional<NativeWebRequest> getRequest() {
        return Optional.empty();
    }

    @Override
    public ResponseEntity<GenerationParametersResponse> getGenerationParameters() {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(parameters.getParameters());
    }

}
