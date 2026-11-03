package tech.genna.TY3.service.generation;

import org.junit.jupiter.api.Test;
import tech.genna.TY3.api.model.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class GenerationParametersTest {
    static GenerateQuizRequest request() {
        return new GenerateQuizRequest(UUID.randomUUID(), QuestionCount.NUMBER_5, Difficulty.MEDIUM,
                QuestionType.SINGLE_CHOICE).name("  Проверка  ");
    }
    @Test void acceptsEveryCatalogueCombinationAndNormalizesDefaults() {
        for (int count : GenerationParameters.COUNTS)
            for (var difficulty : GenerationParameters.DIFFICULTIES)
                for (var type : GenerationParameters.TYPES) {
                    var input = request().questionCount(QuestionCount.fromValue(count)).difficulty(difficulty).questionType(type);
                    var parameters = GenerationParameters.from(input);
                    assertEquals(count, parameters.questionCount());
                    assertEquals(difficulty, parameters.difficulty());
                    assertEquals(type, parameters.questionType());
                    assertEquals("Проверка", parameters.name());
                    assertEquals("ru", parameters.language());
                }
    }
    @Test void rejectsMissingValuesMultipleTypesAndOutOfScopeFeatures() {
        var invalid = List.of(request().materialId(null), request().questionCount(null), request().difficulty(null),
                request().questionType(null),

                request().name("  "), request().name("x".repeat(101)), request().excludeAnswered(true),
                request().prioritizeWeakTopics(true), request().adaptToLevel(true), request().topicIds(Set.of(UUID.randomUUID())));
        for (var input : invalid) assertThrows(IllegalArgumentException.class, () -> GenerationParameters.from(input));
        assertThrows(IllegalArgumentException.class, () -> GenerationParameters.from(null));
    }
}
