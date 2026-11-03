package tech.genna.TY3.service.generation;

import org.junit.jupiter.api.Test;
import tech.genna.TY3.api.model.*;
import java.util.*;
import java.util.stream.IntStream;
import static org.junit.jupiter.api.Assertions.*;

class MlResultValidatorTest {
    final MlResultValidator validator = new MlResultValidator();
    static GenerationParameters parameters() {
        return GenerationParameters.from(GenerationParametersTest.request());
    }
    static MlGenerator.Result result(UUID id, QuestionType type) {
        var options = type == QuestionType.TRUE_FALSE
                ? List.of(new MlGenerator.Option("Верно", true), new MlGenerator.Option("Неверно", false))
                : List.of(new MlGenerator.Option("A", true), new MlGenerator.Option("B", type == QuestionType.MULTIPLE_CHOICE),
                    new MlGenerator.Option("C", false), new MlGenerator.Option("D", false));
        return new MlGenerator.Result(id, IntStream.range(0, 5)
                .mapToObj(i -> new MlGenerator.Question(type, "Вопрос " + i, options, "Объяснение")).toList());
    }
    @Test void acceptsAllThreeQuestionTypes() {
        UUID id = UUID.randomUUID();
        for (var type : QuestionType.values()) validator.validate(id,
                new GenerationParameters(UUID.randomUUID(), "Test", 5, Difficulty.EASY, type, "ru"), result(id, type));
    }
    @Test void rejectsWrongJobCountTypeDuplicateQuestionsAndInvalidAnswers() {
        UUID id = UUID.randomUUID(); var p = parameters(); var valid = result(id, QuestionType.SINGLE_CHOICE);
        var q = valid.questions().getFirst();
        var invalid = List.of(result(UUID.randomUUID(), QuestionType.SINGLE_CHOICE),
                result(id, QuestionType.TRUE_FALSE), new MlGenerator.Result(id, List.of(q)),
                new MlGenerator.Result(id, Collections.nCopies(5, q)),
                new MlGenerator.Result(id, IntStream.range(0, 5).mapToObj(i -> new MlGenerator.Question(q.type(), "Q"+i,
                        List.of(new MlGenerator.Option("A", false), new MlGenerator.Option("B", false),
                                new MlGenerator.Option("C", false), new MlGenerator.Option("D", false)), null)).toList()));
        for (var r : invalid) assertEquals("ML_INVALID_RESULT", assertThrows(GenerationFailure.class, () -> validator.validate(id, p, r)).code());
        assertThrows(GenerationFailure.class, () -> validator.validate(id, p, null));
    }
    @Test void unknownDependencyDetailsNeverReachOwner() {
        var error = GenerationStore.safeError("password=private-secret");
        assertEquals("GENERATION_FAILED", error.getCode());
        assertFalse(error.getMessage().contains("secret"));
    }
}
