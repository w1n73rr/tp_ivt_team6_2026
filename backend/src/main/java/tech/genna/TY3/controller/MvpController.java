package tech.genna.TY3.controller;

import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.request.NativeWebRequest;
import tech.genna.TY3.api.AnalyticsApi;
import tech.genna.TY3.api.AttemptsApi;
import tech.genna.TY3.api.GenerationApi;
import tech.genna.TY3.api.MaterialsApi;
import tech.genna.TY3.api.QuizzesApi;
import tech.genna.TY3.api.TrainingApi;
import tech.genna.TY3.api.UsersApi;

import java.util.Optional;

@RestController
public class MvpController implements MaterialsApi, QuizzesApi, GenerationApi, AttemptsApi,
        TrainingApi, AnalyticsApi, UsersApi {

    @Override
    public Optional<NativeWebRequest> getRequest() {
        return Optional.empty();
    }
}
