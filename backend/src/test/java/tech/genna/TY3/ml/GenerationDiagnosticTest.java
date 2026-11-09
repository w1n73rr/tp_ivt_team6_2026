package tech.genna.TY3.ml;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class GenerationDiagnosticTest {
    @Test void jsonDiagnosticDoesNotEchoProviderBody() {
        var failure = assertThrows(GenerationFailure.class, () -> Json.read("private-material-and-provider-output"));
        assertEquals(GenerationFailure.Code.INVALID_MODEL_OUTPUT, failure.code());
        assertTrue(failure.validationHint().contains("JSON_SYNTAX"));
        assertFalse(failure.validationHint().contains("private-material"));
    }
    @Test void schemaDiagnosticIdentifiesRuleWithoutEchoingUnexpectedValue() {
        var request = QuizGeneratorTest.request(GenerationRequest.QuestionType.single_choice, 5);
        var output = QuizGeneratorTest.output(request.questionType(), 5);
        output.put("requestId", "private-wrong-id");
        var failure = assertThrows(GenerationFailure.class, () -> new QuizValidator().parse(Json.write(output), request));
        assertTrue(failure.validationHint().contains("REQUEST_ID"));
        assertFalse(failure.validationHint().contains("private-wrong-id"));
    }
}
