package tech.genna.TY3.ml;

import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class QuizReviewerTest {
    @Test void malformedVerdictIsRecheckedOnceAndCanStillReject() {
        var request = QuizGeneratorTest.request(GenerationRequest.QuestionType.single_choice, 5);
        var quiz = new QuizValidator().parse(Json.write(QuizGeneratorTest.output(request.questionType(), 5)), request);
        var calls = new java.util.concurrent.atomic.AtomicInteger();
        var reviewer = new QuizReviewer((s,u,f) -> {
            if (calls.incrementAndGet() == 1) return "{\"verdict\":\"pass\",\"issues\":[]";
            assertTrue(u.contains("INVALID_REVIEW_FORMAT"));
            assertTrue(u.contains("Prepared lecture text"));
            assertTrue(u.contains("correctOptionIds"));
            return "{\"verdict\":\"reject\",\"issues\":[{\"questionId\":\"q2\",\"reason\":\"Duplicate meanings\"}]}";
        });
        assertEquals("q2", reviewer.review(request, quiz).getFirst().get("questionId"));
        assertEquals(2, calls.get());
    }

    @Test void repeatedMalformedVerdictsFailAfterTwoCalls() {
        var request = QuizGeneratorTest.request(GenerationRequest.QuestionType.single_choice, 5);
        var quiz = new QuizValidator().parse(Json.write(QuizGeneratorTest.output(request.questionType(), 5)), request);
        var calls = new java.util.concurrent.atomic.AtomicInteger();
        var reviewer = new QuizReviewer((s,u,f) -> { calls.incrementAndGet(); return "{}"; });
        assertThrows(GenerationFailure.class, () -> reviewer.review(request, quiz));
        assertEquals(2, calls.get());
    }

    @Test void reviewTransportFailureIsNeverRetried() {
        var request = QuizGeneratorTest.request(GenerationRequest.QuestionType.single_choice, 5);
        var quiz = new QuizValidator().parse(Json.write(QuizGeneratorTest.output(request.questionType(), 5)), request);
        var calls = new java.util.concurrent.atomic.AtomicInteger();
        var reviewer = new QuizReviewer((s,u,f) -> {
            calls.incrementAndGet(); throw new GenerationFailure(GenerationFailure.Code.RATE_LIMITED);
        });
        assertEquals(GenerationFailure.Code.RATE_LIMITED,
                assertThrows(GenerationFailure.class, () -> reviewer.review(request, quiz)).code());
        assertEquals(1, calls.get());
    }
    @Test void malformedOrContradictoryReviewCannotApproveCandidate() {
        var request = QuizGeneratorTest.request(GenerationRequest.QuestionType.single_choice, 5);
        var quiz = new QuizValidator().parse(Json.write(QuizGeneratorTest.output(request.questionType(), 5)), request);
        for (String raw : List.of("{}", "{\"verdict\":\"pass\",\"issues\":[]} {\"verdict\":\"reject\",\"issues\":[]}", "{\"verdict\":\"pass\",\"issues\":[{\"questionId\":\"q1\",\"reason\":\"Defect\"}]}",
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
