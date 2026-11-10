package tech.genna.TY3.service.quiz;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionSystemException;
import org.springframework.transaction.support.SimpleTransactionStatus;
import org.springframework.web.server.ResponseStatusException;
import tech.genna.TY3.api.model.*;
import tech.genna.TY3.repository.QuizRepository;
import tech.genna.TY3.repository.QuizRepository.*;
import tech.genna.TY3.service.generation.GenerationParameters;
import tools.jackson.databind.json.JsonMapper;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class QuizServiceTest {
    private QuizRepository repository;
    private PlatformTransactionManager transactions;
    private QuizService service;
    private final UUID owner = UUID.randomUUID(), id = UUID.randomUUID(), material = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        repository = mock(QuizRepository.class);
        transactions = mock(PlatformTransactionManager.class);
        when(transactions.getTransaction(any())).thenAnswer(call -> new SimpleTransactionStatus());
        service = new QuizService(repository, transactions);
    }

    @Test
    void emptyListAndTableColumnsAreReturnedForOwner() {
        when(repository.listOwned(owner)).thenReturn(List.of());
        assertTrue(service.list(owner).getItems().isEmpty());
        when(repository.listOwned(owner)).thenReturn(List.of(saved("Тест").summary()));
        var row = service.list(owner).getItems().getFirst();
        assertEquals(id, row.getId());
        assertEquals("Тест", row.getName());
        assertEquals("Исходный материал", row.getMaterialName());
        assertEquals(FileFormat.TXT, row.getFormat());
        assertEquals(5, row.getQuestionCount());
        assertEquals(Difficulty.MEDIUM, row.getDifficulty());
        assertEquals(QuestionType.SINGLE_CHOICE, row.getQuestionType());
    }

    @Test
    void detailContainsSettingsAndPublicQuestionsOnly() {
        when(repository.findOwned(id, owner)).thenReturn(Optional.of(saved("Тест")));
        var response = service.get(id, owner);
        assertNotNull(response.getQuestions().getFirst().getId());
        assertEquals(QuestionCount.NUMBER_5, response.getSettings().getQuestionCount());
        String json = JsonMapper.builder().build().writeValueAsString(response);
        assertFalse(json.contains("correctOptionIds"));
        assertFalse(json.contains("isCorrect"));
        assertFalse(json.contains("explanation"));
    }

    @Test
    void renameNormalizesNameAndPreservesOtherFields() {
        when(repository.findOwned(id, owner)).thenReturn(Optional.of(saved("Old")), Optional.of(saved("New")));
        when(repository.renameOwned(id, owner, "New")).thenReturn(1);
        var result = service.rename(id, owner, "\uFEFF New \u00a0");
        assertEquals("New", result.getName());
        assertEquals(5, result.getQuestionCount());
        assertEquals(saved("Old").summary().getCreatedAt(), result.getCreatedAt());
        verify(repository).renameOwned(id, owner, "New");
        verify(transactions).commit(any());
    }

    @Test
    void nameAtLimitIsAcceptedAfterTrimming() {
        String name = "Я".repeat(100);
        when(repository.findOwned(id, owner)).thenReturn(Optional.of(saved(name)));
        when(repository.renameOwned(id, owner, name)).thenReturn(1);
        assertEquals(name, service.rename(id, owner, "  " + name + "  ").getName());
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   ", "\t\n", "\uFEFF\u00a0"})
    void invalidNamesNeverReachUpdate(String name) {
        when(repository.findOwned(id, owner)).thenReturn(Optional.of(saved("Тест")));
        assertStatus(400, () -> service.rename(id, owner, name));
        verify(repository, never()).renameOwned(any(), any(), any());
        verify(transactions).rollback(any());
    }

    @Test
    void tooLongNameAndDuplicateNameAreRejected() {
        when(repository.findOwned(id, owner)).thenReturn(Optional.of(saved("Тест")));
        assertStatus(400, () -> service.rename(id, owner, "x".repeat(101)));
        when(repository.nameExists(owner, "Duplicate", id)).thenReturn(true);
        var error = assertThrows(ResponseStatusException.class, () -> service.rename(id, owner, "Duplicate"));
        assertEquals("QUIZ_NAME_ALREADY_EXISTS", error.getReason());
        assertEquals(409, error.getStatusCode().value());
        verify(repository, never()).renameOwned(any(), any(), any());
    }

    @Test
    void absentOrForeignQuizIs404ForReadRenameDelete() {
        when(repository.findOwned(id, owner)).thenReturn(Optional.empty());
        assertStatus(404, () -> service.get(id, owner));
        assertStatus(404, () -> service.rename(id, owner, "Name"));
        assertStatus(404, () -> service.delete(id, owner));
        verify(repository, never()).renameOwned(any(), any(), any());
        verify(repository).deleteOwned(id, owner);
    }

    @Test
    void deleteCommitsOwnedRootDeletion() {
        when(repository.deleteOwned(id, owner)).thenReturn(1);
        service.delete(id, owner);
        var order = inOrder(repository, transactions);
        order.verify(repository).lockOwner(owner);
        order.verify(repository).deleteOwned(id, owner);
        order.verify(transactions).commit(any());
    }

    @Test
    void dependencyAndCommitFailuresNeverReturnSuccess() {
        when(repository.listOwned(owner)).thenThrow(new DataAccessResourceFailureException("private connection detail"));
        var error = assertThrows(ResponseStatusException.class, () -> service.list(owner));
        assertEquals("QUIZ_STORAGE_UNAVAILABLE", error.getReason());
        when(repository.deleteOwned(id, owner)).thenReturn(1);
        doThrow(new TransactionSystemException("private commit failure")).when(transactions).commit(any());
        assertStatus(503, () -> service.delete(id, owner));

    }

    private StoredQuiz saved(String name) {
        var summary = new QuizSummary().id(id).name(name).materialId(material).materialName("Исходный материал")
                .format(FileFormat.TXT).questionCount(5).difficulty(Difficulty.MEDIUM).questionType(QuestionType.SINGLE_CHOICE)
                .createdAt(OffsetDateTime.parse("2026-11-09T10:00:00Z"));
        var result = JsonMapper.builder().build().readTree("""
                {"questions":[{"id":"00000000-0000-0000-0000-000000000001","type":"single_choice","text":"Вопрос",
                "correctOptionIds":["00000000-0000-0000-0000-000000000002"],"explanation":"Секрет",
                "options":[{"id":"00000000-0000-0000-0000-000000000002","text":"Вариант"}]}]}
                """);
        return new StoredQuiz(summary, new GenerationParameters(material, name, 5, Difficulty.MEDIUM, QuestionType.SINGLE_CHOICE, "ru"), result);
    }

    private static void assertStatus(int code, org.junit.jupiter.api.function.Executable action) {
        assertEquals(code, assertThrows(ResponseStatusException.class, action).getStatusCode().value());
    }
}
