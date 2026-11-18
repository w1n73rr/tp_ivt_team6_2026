package tech.genna.TY3.ml;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import static org.junit.jupiter.api.Assertions.*;

class MlConfigurationTest {
    private final ApplicationContextRunner context = new ApplicationContextRunner()
            .withUserConfiguration(MlConfiguration.class);

    @Test void cloudruUsesItsOwnSettingsAndMissingKeyFailsSafely() {
        context.withPropertyValues("ML_PROVIDER=cloudru", "CLOUDRU_API_KEY=",
                "ml.openrouter.model=unused-paid-model").run(app -> {
            assertNull(app.getStartupFailure());
            var request = new GenerationRequest("config-test", "material", "Учебная лекция",
                    5, GenerationRequest.Difficulty.easy, GenerationRequest.QuestionType.single_choice);
            assertEquals(GenerationFailure.Code.NOT_CONFIGURED, assertThrows(GenerationFailure.class,
                    () -> app.getBean(QuizGenerator.class).generate(request, event -> {})).code());
        });
    }

    @Test void rejectsUnknownProviderAndInvalidCloudTimeout() {
        context.withPropertyValues("ML_PROVIDER=typo").run(app -> assertNotNull(app.getStartupFailure()));
        context.withPropertyValues("ML_PROVIDER=cloudru", "CLOUDRU_TIMEOUT_SECONDS=0")
                .run(app -> assertNotNull(app.getStartupFailure()));
    }
}
