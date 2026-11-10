package tech.genna.TY3.service.generation;

import org.junit.jupiter.api.Test;
import tech.genna.TY3.api.model.*;
import tech.genna.TY3.ml.GenerationRequest;
import tech.genna.TY3.ml.Quiz;
import tech.genna.TY3.support.QuizFixtures;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

class SavedQuizTest {
    private final UUID job = UUID.randomUUID(), material = UUID.randomUUID();
    private final GenerationParameters parameters = new GenerationParameters(material, "Name", 5,
            Difficulty.MEDIUM, QuestionType.SINGLE_CHOICE, "ru");
    private final GenerationRequest request = new GenerationRequest(job.toString(), material.toString(), "Text", 5,
            GenerationRequest.Difficulty.medium, GenerationRequest.QuestionType.single_choice);

    @Test
    void correctAnswersPointToFreshPersistedOptionIds() {
        var quiz = QuizFixtures.valid(request);
        var saved = SavedQuiz.from(new MlGenerator.Result(job, List.of(), quiz), parameters);
        Set<UUID> ids = new HashSet<>();
        for (var q : saved.questions()) {
            assertTrue(ids.add(q.id()));
            q.options().forEach(o -> assertTrue(ids.add(o.id())));
            assertEquals(List.of(q.options().stream().filter(o -> o.sourceId().equals("b")).findFirst().orElseThrow().id()), q.correctOptionIds());
        }
    }

    @Test
    void malformedRawQuizIsRejectedEvenIfAdapterProjectionLooksValid() {
        var quiz = QuizFixtures.valid(request);
        var bad = new Quiz(2, material.toString(), quiz.title(), quiz.questions().subList(0, 4));
        var error = assertThrows(GenerationFailure.class,
                () -> SavedQuiz.from(new MlGenerator.Result(job, List.of(), bad), parameters));
        assertEquals("ML_INVALID_RESULT", error.code());
    }

    @Test
    void wrongMaterialAndMissingRawQuizAreRejected() {
        var quiz = QuizFixtures.valid(request);
        var wrongMaterial = new Quiz(2, UUID.randomUUID().toString(), quiz.title(), quiz.questions());
        assertThrows(GenerationFailure.class, () -> SavedQuiz.from(new MlGenerator.Result(job, List.of(), wrongMaterial), parameters));
        assertThrows(GenerationFailure.class, () -> SavedQuiz.from(new MlGenerator.Result(job, List.of(), null), parameters));
    }
}
