package tech.genna.TY3.tests.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

@Tag("attempt-passing")
class AttemptPassingAssertionsTest {
    private static final ObjectMapper JSON = new ObjectMapper();

    @Test
    void publicSelectionWithoutGradeIsAllowed() throws Exception {
        assertDoesNotThrow(() -> AttemptPassingApiTest.assertPublic(JSON.readTree(
                "{\"questionId\":\"q\",\"selectedOptionIds\":[\"a\"],\"savedAt\":\"2026-11-20T00:00:00Z\"}")));
    }

    @Test
    void nestedCorrectnessIsRejected() throws Exception {
        var response = JSON.readTree("{\"questionId\":\"q\",\"selectedOptionIds\":[],\"savedAt\":\"time\",\"extra\":{\"isCorrect\":true}}");
        assertThrows(AssertionError.class, () -> AttemptPassingApiTest.assertPublic(response));
    }

    @Test
    void unknownAliasInQuestionOptionIsRejected() throws Exception {
        var response = JSON.readTree("""
                {"attempt":{"id":"p","quiz":{},"status":"IN_PROGRESS","startedAt":"time","answeredCount":0},
                 "questions":[{"id":"q","type":"SINGLE_CHOICE","text":"QA","position":1,
                    "options":[{"id":"a","text":"QA","answerKey":true}]}],
                 "answers":[],"currentPosition":1}
                """);
        assertThrows(AssertionError.class, () -> AttemptPassingApiTest.assertPublic(response));
    }

    @Test
    void disjointTotalsAndPercentageAreAccepted() throws Exception {
        var response = JSON.readTree("{\"totalQuestions\":5,\"correctCount\":3,\"incorrectCount\":1,\"skippedCount\":1,\"score\":3,\"scorePercent\":60}");
        assertDoesNotThrow(() -> AttemptPassingApiTest.assertTotals(response, 5, 3, 1, 1));
    }

    @Test
    void wrongPercentageAndDoubleCountedSkipAreRejected() throws Exception {
        var wrongPercent = JSON.readTree("{\"totalQuestions\":5,\"correctCount\":3,\"incorrectCount\":1,\"skippedCount\":1,\"score\":3,\"scorePercent\":80}");
        var doubleSkip = JSON.readTree("{\"totalQuestions\":5,\"correctCount\":3,\"incorrectCount\":2,\"skippedCount\":1,\"score\":3,\"scorePercent\":60}");
        assertThrows(AssertionError.class, () -> AttemptPassingApiTest.assertTotals(wrongPercent, 5, 3, 1, 1));
        assertThrows(AssertionError.class, () -> AttemptPassingApiTest.assertTotals(doubleSkip, 5, 3, 1, 1));
    }

    @Test
    void missingZeroCounterIsRejected() throws Exception {
        var response = JSON.readTree("""
                {"totalQuestions":5,"correctCount":5,"skippedCount":0,"score":5,"scorePercent":100}
                """);
        assertThrows(AssertionError.class, () -> AttemptPassingApiTest.assertTotals(response, 5, 5, 0, 0));
    }
}
