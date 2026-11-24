package tech.genna.TY3.service.attempt;

import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.transaction.*;
import org.springframework.transaction.support.SimpleTransactionStatus;
import org.springframework.web.server.ResponseStatusException;
import tech.genna.TY3.api.model.*;
import tech.genna.TY3.repository.*;
import tech.genna.TY3.repository.AttemptRepository.Attempt;
import tools.jackson.databind.json.JsonMapper;
import java.time.OffsetDateTime;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class AttemptStartServiceTest {
    final UUID owner=UUID.randomUUID(), quiz=UUID.randomUUID(), id=UUID.randomUUID();
    final UUID q=UUID.randomUUID(), second=UUID.randomUUID(), option=UUID.randomUUID();
    final String key=UUID.randomUUID().toString();
    final JsonMapper json=JsonMapper.builder().findAndAddModules().build();
    AttemptRepository attempts;
    QuizRepository quizzes;
    PlatformTransactionManager transactions;
    AttemptService service;

    @BeforeEach void setup() {
        attempts=mock(AttemptRepository.class);quizzes=mock(QuizRepository.class);
        transactions=mock(PlatformTransactionManager.class);
        when(transactions.getTransaction(any())).thenAnswer(c -> new SimpleTransactionStatus());
        service=new AttemptService(attempts,quizzes,json,transactions);
        source("single_choice");
    }
    Attempt source(String type) {
        var questions=json.createArrayNode();
        for(UUID question:List.of(q,second)) {
            var node=questions.addObject().put("id",question.toString()).put("type",type).put("text","Question")
                    .put("explanation","Private explanation").put("correct",true).put("score",1);
            node.putArray("options").addObject().put("id",option.toString()).put("text","A");
            node.putArray("correctOptionIds").add(option.toString());
        }
        var summary=new QuizSummary().id(quiz).name("Original").questionCount(2);
        var result=json.createObjectNode();result.set("questions",questions);
        when(quizzes.findOwned(quiz,owner)).thenReturn(Optional.of(new QuizRepository.StoredQuiz(summary,null,result)));
        return new Attempt(id,owner,quiz,json.valueToTree(summary),questions,json.createObjectNode(),"IN_PROGRESS",OffsetDateTime.now());
    }
    @ParameterizedTest @ValueSource(strings={"single_choice","multiple_choice","true_false"})
    void startsEmptySnapshotWithPublicQuestionsAndServerOrder(String type) {
        source(type);
        var started=service.start(quiz,owner,key);
        assertTrue(started.created());var detail=started.detail();
        assertEquals(AttemptStatus.IN_PROGRESS,detail.getAttempt().getStatus());
        assertEquals(quiz,detail.getAttempt().getQuiz().getId());assertNotNull(detail.getAttempt().getId());
        assertEquals(0,detail.getAttempt().getAnsweredCount());assertNull(detail.getAttempt().getScorePercent());
        assertTrue(detail.getAnswers().isEmpty());assertEquals(1,detail.getCurrentPosition());
        assertEquals(List.of(q,second),detail.getQuestions().stream().map(Question::getId).toList());
        assertEquals(List.of(1,2),detail.getQuestions().stream().map(Question::getPosition).toList());
        assertEquals(QuestionType.fromValue(type.toUpperCase(Locale.ROOT)),detail.getQuestions().getFirst().getType());
        assertEquals(option,detail.getQuestions().getFirst().getOptions().getFirst().getId());
        String publicJson=json.writeValueAsString(detail);
        assertFalse(publicJson.contains("correctOptionIds"));assertFalse(publicJson.contains("Private explanation"));
        assertFalse(publicJson.contains("\"correct\""));assertFalse(publicJson.contains("\"score\""));
        verify(attempts).create(argThat(a -> a.owner().equals(owner) && a.quizId().equals(quiz) && a.answers().isEmpty()),eq(key));
        verify(attempts).bindStartKey(owner,key,detail.getAttempt().getId());
    }
    @Test void newKeyResumesActiveAndRestoresSelectionAndPosition() {
        var active=source("single_choice");
        ((tools.jackson.databind.node.ObjectNode)active.answers()).set(q.toString(),
                json.valueToTree(new SavedAnswer(q,Set.of(option),OffsetDateTime.now())));
        when(attempts.active(quiz,owner)).thenReturn(Optional.of(active));
        var resumed=service.start(quiz,owner,key);
        assertFalse(resumed.created());assertEquals(id,resumed.detail().getAttempt().getId());
        assertEquals(1,resumed.detail().getAttempt().getAnsweredCount());assertEquals(2,resumed.detail().getCurrentPosition());
        assertEquals(Set.of(option),resumed.detail().getAnswers().getFirst().getSelectedOptionIds());
        verify(attempts,never()).create(any(),any());verify(attempts).bindStartKey(owner,key,id);
        // Continuing does not replace the old snapshot with the potentially renamed source quiz.
        assertEquals("Original",resumed.detail().getAttempt().getQuiz().getName());
    }
    @Test void sameKeyReplaysSameAttemptEvenAfterCompletion() {
        var snapshot=source("single_choice");
        var completed=new Attempt(id,owner,quiz,snapshot.quiz(),snapshot.questions(),snapshot.answers(),"COMPLETED",snapshot.startedAt());
        when(attempts.replay(owner,key)).thenReturn(Optional.of(id));
        when(attempts.find(id,owner,true)).thenReturn(Optional.of(completed));
        var replayed=service.start(quiz,owner,key);
        assertFalse(replayed.created());assertEquals(id,replayed.detail().getAttempt().getId());
        assertEquals(AttemptStatus.COMPLETED,replayed.detail().getAttempt().getStatus());
        verify(attempts,never()).create(any(),any());verify(attempts,never()).bindStartKey(any(),any(),any());
        verify(attempts,never()).active(any(),any());
    }
    @Test void newKeyAfterCompletionCreatesSeparateEmptyAttempt() {
        var snapshot=source("single_choice");String originalKey=UUID.randomUUID().toString();
        var completed=new Attempt(id,owner,quiz,snapshot.quiz(),snapshot.questions(),snapshot.answers(),"COMPLETED",snapshot.startedAt());
        when(attempts.replay(owner,originalKey)).thenReturn(Optional.of(id));
        when(attempts.find(id,owner,true)).thenReturn(Optional.of(completed));
        assertEquals(id,service.start(quiz,owner,originalKey).detail().getAttempt().getId());
        var result=service.start(quiz,owner,key);
        assertTrue(result.created());assertNotEquals(id,result.detail().getAttempt().getId());
        assertTrue(result.detail().getAnswers().isEmpty());
    }
    @Test void reusedKeyForDifferentAccessibleQuizIsConflict() {
        var snapshot=source("single_choice");
        var different=new Attempt(id,owner,UUID.randomUUID(),snapshot.quiz(),snapshot.questions(),snapshot.answers(),"IN_PROGRESS",snapshot.startedAt());
        when(attempts.replay(owner,key)).thenReturn(Optional.of(id));when(attempts.find(id,owner,true)).thenReturn(Optional.of(different));
        assertEquals(409,assertThrows(ResponseStatusException.class,()->service.start(quiz,owner,key)).getStatusCode().value());
        verify(attempts,never()).create(any(),any());verify(attempts,never()).bindStartKey(any(),any(),any());
    }
    @Test void missingAndForeignQuizAreHiddenBeforeReplay() {
        when(quizzes.findOwned(quiz,owner)).thenReturn(Optional.empty());
        assertEquals(404,assertThrows(ResponseStatusException.class,()->service.start(quiz,owner,key)).getStatusCode().value());
        verifyNoInteractions(attempts);
    }
    @ParameterizedTest @ValueSource(strings={"","bad-key","1-1-1-1-1"})
    void invalidKeyIsRejectedBeforeStorage(String invalid) {
        assertEquals(400,assertThrows(ResponseStatusException.class,()->service.start(quiz,owner,invalid)).getStatusCode().value());
        verifyNoInteractions(attempts,transactions);
    }
    @Test void dependencyAndCommitErrorsAreSafe() {
        doThrow(new DataAccessResourceFailureException("Private SQL")).when(attempts).bindStartKey(any(),any(),any());
        assertEquals("ATTEMPT_STORAGE_UNAVAILABLE",assertThrows(ResponseStatusException.class,()->service.start(quiz,owner,key)).getReason());
        reset(attempts);
        doThrow(new TransactionSystemException("Private commit")).when(transactions).commit(any());
        assertEquals(503,assertThrows(ResponseStatusException.class,()->service.start(quiz,owner,key)).getStatusCode().value());
    }
}
