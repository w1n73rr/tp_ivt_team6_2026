package tech.genna.TY3.service.attempt;

import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.transaction.PlatformTransactionManager;
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

class AttemptServiceTest {
    final UUID id=UUID.randomUUID(), owner=UUID.randomUUID(), q=UUID.randomUUID(), x=UUID.randomUUID(), y=UUID.randomUUID();
    final JsonMapper json=JsonMapper.builder().findAndAddModules().build();
    AttemptRepository repo; AttemptService service;
    @BeforeEach void setup() {
        repo=mock(AttemptRepository.class);
        var manager=mock(PlatformTransactionManager.class);
        when(manager.getTransaction(any())).thenAnswer(c -> new SimpleTransactionStatus());
        service=new AttemptService(repo,mock(QuizRepository.class),json,manager);
    }
    Attempt fixture(String type,String status) {
        var questions=json.createArrayNode();
        var question=questions.addObject().put("id",q.toString()).put("type",type).put("text","Question");
        var options=question.putArray("options");
        options.addObject().put("id",x.toString()).put("text","A");
        options.addObject().put("id",y.toString()).put("text","B");
        question.putArray("correctOptionIds").add(x.toString());
        var a=new Attempt(id,owner,UUID.randomUUID(),json.valueToTree(new QuizSummary()),questions,json.createObjectNode(),status,OffsetDateTime.now());
        when(repo.find(id,owner,true)).thenReturn(Optional.of(a));
        when(repo.find(id,owner,false)).thenReturn(Optional.of(a));
        return a;
    }
    @ParameterizedTest @ValueSource(strings={"single_choice","multiple_choice","true_false"})
    void savesReplacesAndClears(String type) {
        var a=fixture(type,"IN_PROGRESS");
        var first=service.save(id,owner,q,new SaveAnswerRequest(Set.of(x)));
        assertEquals(Set.of(x),first.getSelectedOptionIds());
        ((tools.jackson.databind.node.ObjectNode)a.answers()).set(q.toString(),json.valueToTree(first));
        clearInvocations(repo);
        assertEquals(first,service.save(id,owner,q,new SaveAnswerRequest(Set.of(x))));
        verify(repo,never()).answers(any(),any(),any());
        assertEquals(Set.of(y),service.save(id,owner,q,new SaveAnswerRequest(Set.of(y))).getSelectedOptionIds());
        assertTrue(service.save(id,owner,q,new SaveAnswerRequest(Set.of())).getSelectedOptionIds().isEmpty());
    }
    @ParameterizedTest @ValueSource(strings={"single_choice","true_false"})
    void singleTypesRejectTwoOptions(String type) {
        fixture(type,"IN_PROGRESS");
        assertEquals(400,assertThrows(ResponseStatusException.class,()->service.save(id,owner,q,new SaveAnswerRequest(Set.of(x,y)))).getStatusCode().value());
        verify(repo,never()).answers(any(),any(),any());
    }
    @Test void multipleAcceptsSetAndCanonicalizesOrder() {
        fixture("multiple_choice","IN_PROGRESS");
        assertEquals(Set.of(x,y),service.save(id,owner,q,new SaveAnswerRequest(Set.of(y,x))).getSelectedOptionIds());
    }
    @Test void rejectsForeignQuestionOptionsAndDuplicatesWithoutWriting() {
        fixture("multiple_choice","IN_PROGRESS");
        assertThrows(ResponseStatusException.class,()->service.save(id,owner,UUID.randomUUID(),new SaveAnswerRequest(Set.of(x))));
        assertThrows(ResponseStatusException.class,()->service.save(id,owner,q,new SaveAnswerRequest(Set.of(UUID.randomUUID()))));
        assertThrows(ResponseStatusException.class,()->service.save(id,owner,q,new SaveAnswerRequest().selectedOptionIds(null)));
        verify(repo,never()).answers(any(),any(),any());
    }
    @Test void closedAndOtherOwnerCannotWrite() {
        fixture("single_choice","COMPLETED");
        assertEquals(409,assertThrows(ResponseStatusException.class,()->service.save(id,owner,q,new SaveAnswerRequest(Set.of(x)))).getStatusCode().value());
        assertEquals(404,assertThrows(ResponseStatusException.class,()->service.save(id,UUID.randomUUID(),q,new SaveAnswerRequest(Set.of(x)))).getStatusCode().value());
        verify(repo,never()).answers(any(),any(),any());
    }
    @Test void dependencyFailureIsSafe() {
        when(repo.find(id,owner,true)).thenThrow(new org.springframework.dao.DataAccessResourceFailureException("private SQL"));
        var ex=assertThrows(ResponseStatusException.class,()->service.save(id,owner,q,new SaveAnswerRequest(Set.of(x))));
        assertEquals(503,ex.getStatusCode().value()); assertFalse(ex.getMessage().contains("private SQL"));
    }
    @Test void continuationDoesNotExposeKeys() {
        fixture("single_choice","IN_PROGRESS");
        var response=json.writeValueAsString(service.get(id,owner));
        assertFalse(response.contains("correctOptionIds"));
        assertFalse(response.contains("explanation"));
    }
    @Test void completionUsesOnlyServerSnapshotAndReplayReturnsStoredResult() {
        var a=fixture("single_choice","IN_PROGRESS");
        ((tools.jackson.databind.node.ObjectNode)a.answers()).set(q.toString(),json.valueToTree(new SavedAnswer(q,Set.of(x),OffsetDateTime.now())));
        var result=service.complete(id,owner);
        assertEquals(1,result.getScore());assertEquals(100,result.getScorePercent());
        verify(repo).complete(id,owner,result);
        fixture("single_choice","COMPLETED");
        when(repo.completion(id,owner)).thenReturn(result);
        clearInvocations(repo);
        assertSame(result,service.complete(id,owner));
        verify(repo,never()).complete(any(),any(),any());
    }
    @Test void completionRejectsAbandonedOrForeignAttemptAndDependencyFailure() {
        fixture("single_choice","ABANDONED");
        assertEquals(409,assertThrows(ResponseStatusException.class,()->service.complete(id,owner)).getStatusCode().value());
        assertEquals(404,assertThrows(ResponseStatusException.class,()->service.complete(id,UUID.randomUUID())).getStatusCode().value());
        fixture("single_choice","IN_PROGRESS");
        doThrow(new org.springframework.dao.DataAccessResourceFailureException("private SQL")).when(repo).complete(any(),any(),any());
        assertEquals(503,assertThrows(ResponseStatusException.class,()->service.complete(id,owner)).getStatusCode().value());
    }
    @Test void resultRequiresCompletedOwnerAndReturnsStoredTotalsWithOrderedComparison() {
        fixture("single_choice","IN_PROGRESS");
        assertEquals(409,assertThrows(ResponseStatusException.class,()->service.result(id,owner)).getStatusCode().value());
        assertEquals(404,assertThrows(ResponseStatusException.class,()->service.result(id,UUID.randomUUID())).getStatusCode().value());
        var a=fixture("single_choice","COMPLETED");
        ((tools.jackson.databind.node.ObjectNode)a.answers()).set(q.toString(),json.valueToTree(new SavedAnswer(q,Set.of(y),OffsetDateTime.now())));
        var stored=new AttemptCompletion().attemptId(id).quiz(new QuizSummary()).totalQuestions(1)
                .correctCount(0).incorrectCount(1).skippedCount(0).score(0).scorePercent(0.0)
                .durationSeconds(42L).completedAt(OffsetDateTime.now());
        when(repo.completion(id,owner)).thenReturn(stored);
        var result=service.result(id,owner);
        assertEquals(42L,result.getDurationSeconds());assertEquals(1,result.getIncorrectCount());
        assertEquals(0,result.getSkippedCount());assertEquals(stored.getCompletedAt(),result.getCompletedAt());
        assertEquals(q,result.getItems().getFirst().getQuestion().getId());
        assertEquals(1,result.getItems().getFirst().getQuestion().getPosition());
        assertEquals(Set.of(y),result.getItems().getFirst().getSelectedOptionIds());
        assertEquals(Set.of(x),result.getItems().getFirst().getCorrectOptionIds());
        assertEquals(QuestionResult.OutcomeEnum.INCORRECT,result.getItems().getFirst().getOutcome());
        verify(repo,never()).complete(any(),any(),any());
    }
    @Test void resultStorageFailureIsSafe() {
        fixture("single_choice","COMPLETED");
        when(repo.completion(id,owner)).thenThrow(new org.springframework.dao.DataAccessResourceFailureException("private result"));
        assertEquals(503,assertThrows(ResponseStatusException.class,()->service.result(id,owner)).getStatusCode().value());
    }
    @Test void actualOptionOfAnotherQuestionCannotReplaceThisAnswer() {
        var a=fixture("single_choice","IN_PROGRESS");
        UUID secondQuestion=UUID.randomUUID(),secondOption=UUID.randomUUID();
        var extra=((tools.jackson.databind.node.ArrayNode)a.questions()).addObject()
                .put("id",secondQuestion.toString()).put("type","single_choice").put("text","Other question");
        extra.putArray("options").addObject().put("id",secondOption.toString()).put("text","Other option");
        extra.putArray("correctOptionIds").add(secondOption.toString());
        var saved=service.save(id,owner,q,new SaveAnswerRequest(Set.of(x)));
        ((tools.jackson.databind.node.ObjectNode)a.answers()).set(q.toString(),json.valueToTree(saved));
        clearInvocations(repo);
        assertEquals(400,assertThrows(ResponseStatusException.class,
                ()->service.save(id,owner,q,new SaveAnswerRequest(Set.of(secondOption)))).getStatusCode().value());
        verify(repo,never()).answers(any(),any(),any());
        assertEquals(saved,json.treeToValue(a.answers().get(q.toString()),SavedAnswer.class));
    }
}
