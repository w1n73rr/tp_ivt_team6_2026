package tech.genna.TY3.service.generation;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.web.server.ResponseStatusException;
import tech.genna.TY3.api.model.*;
import tech.genna.TY3.repository.GenerationOptionsRepository;
import tech.genna.TY3.repository.GenerationOptionsRepository.Option;
import tech.genna.TY3.repository.GenerationSettingsSnapshotRepository;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

public class GenerationParametersServiceTest {
    private GenerationOptionsRepository options;
    private GenerationSettingsSnapshotRepository snapshots;
    private GenerationParametersService service;

    public static List<Option> initialOptions() {
        return List.of(new Option("QUESTION_COUNT", "5", true, false),
                new Option("QUESTION_COUNT", "10", true, true),
                new Option("DIFFICULTY", "EASY", true, false),
                new Option("DIFFICULTY", "MEDIUM", true, true),
                new Option("DIFFICULTY", "HARD", true, false),
                new Option("QUESTION_TYPE", "SINGLE_CHOICE", true, true),
                new Option("QUESTION_TYPE", "MULTIPLE_CHOICE", true, false),
                new Option("QUESTION_TYPE", "TRUE_FALSE", true, false));
    }

    @BeforeEach
    void setUp() {
        options = mock(GenerationOptionsRepository.class);
        snapshots = mock(GenerationSettingsSnapshotRepository.class);
        service = new GenerationParametersService(options, snapshots);
        when(options.findAll()).thenReturn(initialOptions());
    }

    @Test
    void exposesAllValuesAvailabilityAndDatabaseDefaults() {
        var result = service.getParameters();
        assertEquals(List.of(5, 10), result.getQuestionCounts().stream().map(o -> o.getValue().getValue()).toList());
        assertEquals(3, result.getDifficulties().size());
        assertEquals(3, result.getQuestionTypes().size());
        assertTrue(result.getQuestionTypes().stream().allMatch(QuestionTypeOption::getAvailable));
        assertEquals(10, result.getDefaults().getQuestionCount().getValue());
        assertEquals(Difficulty.MEDIUM, result.getDefaults().getDifficulty());
        assertEquals(QuestionType.SINGLE_CHOICE, result.getDefaults().getQuestionType());
        assertEquals(GenerationParametersResponse.QuestionTypeSelectionEnum.SINGLE, result.getQuestionTypeSelection());
    }

    @ParameterizedTest
    @CsvSource({"5,EASY,SINGLE_CHOICE", "10,MEDIUM,MULTIPLE_CHOICE", "5,HARD,TRUE_FALSE"})
    void acceptsSupportedSettingsAndMapsMlCodes(int count, Difficulty difficulty, QuestionType type) {
        var selected = service.validate(request(count, difficulty, type));
        assertEquals(count, selected.questionCount());
        assertEquals(difficulty.getValue().toLowerCase(java.util.Locale.ROOT), selected.mlDifficulty());
        assertEquals(type.getValue().toLowerCase(java.util.Locale.ROOT), selected.mlQuestionType());
        verifyNoInteractions(snapshots);
    }

    @Test
    void freshReadsExposeChangesAndUseSameAvailabilityForValidation() {
        var changed = initialOptions().stream().map(o -> o.category().equals("QUESTION_COUNT")
                ? new Option(o.category(), o.value(), !o.value().equals("10"), o.value().equals("5")) : o).toList();
        when(options.findAll()).thenReturn(initialOptions(), changed, changed);
        assertEquals(10, service.getParameters().getDefaults().getQuestionCount().getValue());
        var response = service.getParameters();
        assertEquals(5, response.getDefaults().getQuestionCount().getValue());
        assertFalse(response.getQuestionCounts().getLast().getAvailable());
        assertStatus(400, () -> service.validate(request(10, Difficulty.MEDIUM, QuestionType.SINGLE_CHOICE)));
    }

    @ParameterizedTest
    @CsvSource({"QUESTION_COUNT,5", "DIFFICULTY,HARD", "QUESTION_TYPE,TRUE_FALSE"})
    void rejectsDisabledValueInEveryCategory(String category, String value) {
        when(options.findAll()).thenReturn(initialOptions().stream().map(o ->
                o.category().equals(category) && o.value().equals(value)
                        ? new Option(category, value, false, false) : o).toList());
        assertStatus(400, () -> service.validate(request(5, Difficulty.HARD, QuestionType.TRUE_FALSE)));
    }

    @Test
    void rejectsMissingSettings() {
        assertStatus(400, () -> service.validate(null));
        assertStatus(400, () -> service.validate(request(null, Difficulty.EASY, QuestionType.SINGLE_CHOICE)));
        assertStatus(400, () -> service.validate(request(5, null, QuestionType.SINGLE_CHOICE)));
        assertStatus(400, () -> service.validate(request(5, Difficulty.EASY, null)));
    }

    @Test
    void failsClosedOnEmptyIncompleteOrInvalidCatalog() {
        var noDefaults = initialOptions().stream().map(o -> new Option(o.category(), o.value(), true, false)).toList();
        var unavailableDefault = initialOptions().stream().map(o -> new Option(o.category(), o.value(), false, o.defaultValue())).toList();
        var duplicate = new ArrayList<>(initialOptions());
        duplicate.set(1, duplicate.getFirst());
        for (var broken : List.of(List.<Option>of(), initialOptions().subList(1, 8), noDefaults, unavailableDefault, duplicate)) {
            when(options.findAll()).thenReturn(broken);
            assertStatus(503, service::getParameters);
            assertStatus(503, () -> service.validate(request(5, Difficulty.MEDIUM, QuestionType.SINGLE_CHOICE)));
        }
    }

    @Test
    void dependencyErrorsBecomeServiceUnavailableWithoutFallback() {
        when(options.findAll()).thenThrow(new DataAccessResourceFailureException("private database detail"));
        var error = assertThrows(ResponseStatusException.class, service::getParameters);
        assertEquals(503, error.getStatusCode().value());
        assertEquals("GENERATION_CONFIG_UNAVAILABLE", error.getReason());
        assertStatus(503, () -> service.saveForRun(UUID.randomUUID(), request(5, Difficulty.MEDIUM, QuestionType.SINGLE_CHOICE)));
        verifyNoInteractions(snapshots);
    }

    @Test
    void persistsValidatedValuesAndPropagatesSnapshotFailure() {
        var jobId = UUID.randomUUID();
        var request = request(5, Difficulty.MEDIUM, QuestionType.SINGLE_CHOICE);
        var settings = service.saveForRun(jobId, request);
        verify(snapshots).insert(jobId, settings);
        doThrow(new DataAccessResourceFailureException("private detail")).when(snapshots).insert(any(), any());
        assertStatus(503, () -> service.saveForRun(UUID.randomUUID(), request));
    }

    private static GenerateQuizRequest request(Integer count, Difficulty difficulty, QuestionType type) {
        return new GenerateQuizRequest(UUID.randomUUID(), count == null ? null : QuestionCount.fromValue(count), difficulty, type);
    }

    private static void assertStatus(int status, org.junit.jupiter.api.function.Executable operation) {
        assertEquals(status, assertThrows(ResponseStatusException.class, operation).getStatusCode().value());
    }
}
