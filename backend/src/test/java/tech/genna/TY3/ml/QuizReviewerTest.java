package tech.genna.TY3.ml;

import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class QuizReviewerTest {
    @Test void malformedOrContradictoryReviewCannotApproveCandidate() {
        var request = QuizGeneratorTest.request(GenerationRequest.QuestionType.single_choice, 5);
        var quiz = new QuizValidator().parse(Json.write(QuizGeneratorTest.output(request.questionType(), 5)), request);
        for (String raw : List.of("{}", "{\"verdict\":\"pass\",\"issues\":[{\"questionId\":\"q1\",\"reason\":\"Defect\"}]}",
                "{\"verdict\":\"reject\",\"issues\":[]}",
                "{\"verdict\":\"reject\",\"issues\":[{\"questionId\":\"q999\",\"reason\":\"Defect\"}]}")) {
            var reviewer = new QuizReviewer((s,u,f)->raw);
            assertEquals(GenerationFailure.Code.INVALID_MODEL_OUTPUT,
                    assertThrows(GenerationFailure.class,()->reviewer.review(request,quiz)).code());
        }
    }

    @Test void reviewerReceivesSourceAndCandidateAndCanReturnConcreteIssue() {
        var request = QuizGeneratorTest.request(GenerationRequest.QuestionType.multiple_choice, 5);
        var quiz = new QuizValidator().parse(Json.write(QuizGeneratorTest.output(request.questionType(),5)),request);
        var reviewer = new QuizReviewer((s,u,f)->{
            assertTrue(u.contains("Prepared lecture text"));
            assertTrue(u.contains("correctOptionIds"));
            return "{\"verdict\":\"reject\",\"issues\":[{\"questionId\":\"q3\",\"reason\":\"Two options paraphrase the same fact\"}]}";
        });
        assertEquals("q3",reviewer.review(request,quiz).getFirst().get("questionId"));
    }
}
