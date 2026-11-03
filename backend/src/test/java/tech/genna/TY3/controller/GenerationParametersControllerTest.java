package tech.genna.TY3.controller;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import tech.genna.TY3.repository.GenerationOptionsRepository;
import tech.genna.TY3.repository.GenerationSettingsSnapshotRepository;
import tech.genna.TY3.service.generation.GenerationParametersService;
import tech.genna.TY3.service.generation.GenerationParametersServiceTest;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class GenerationParametersControllerTest {
    private MockMvc mvc;
    private GenerationOptionsRepository options;
    private GenerationSettingsSnapshotRepository snapshots;
    private static final String REQUEST = """
            {"materialId":"00000000-0000-0000-0000-000000000001",
            "questionCount":5,"difficulty":"MEDIUM","questionType":"SINGLE_CHOICE"}
            """;

    @BeforeEach
    void setUp() {
        options = mock(GenerationOptionsRepository.class);
        snapshots = mock(GenerationSettingsSnapshotRepository.class);
        when(options.findAll()).thenReturn(GenerationParametersServiceTest.initialOptions());
        var service = new GenerationParametersService(options, snapshots);
        mvc = MockMvcBuilders.standaloneSetup(new GenerationParametersController(service))
                .setControllerAdvice(new GlobalExceptionHandler()).build();
    }

    @Test
    void responseMatchesGeneratedContractAndIsNotCached() throws Exception {
        mvc.perform(get("/api/generation/parameters"))
                .andExpect(status().isOk()).andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.questionCounts[0].value").value(5))
                .andExpect(jsonPath("$.questionCounts[1].value").value(10))
                .andExpect(jsonPath("$.questionTypes.length()").value(3))
                .andExpect(jsonPath("$.questionTypes[0].available").value(true))
                .andExpect(jsonPath("$.defaults.questionType").value("SINGLE_CHOICE"))
                .andExpect(jsonPath("$.questionTypeSelection").value("SINGLE"))
                .andExpect(jsonPath("$.topics").doesNotExist());
    }

    @ParameterizedTest
    @ValueSource(strings = {"20", "0", "7"})
    void rejectsInvalidCountsAtHttpBoundary(String count) throws Exception {
        mvc.perform(post("/api/quizzes").header("Idempotency-Key", java.util.UUID.randomUUID()).contentType(MediaType.APPLICATION_JSON)
                        .content(REQUEST.replace("\"questionCount\":5", "\"questionCount\":" + count)))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"\"questionType\":\"OPEN\"", "\"questionType\":[\"SINGLE_CHOICE\",\"TRUE_FALSE\"]",
            "\"questionTypes\":[\"SINGLE_CHOICE\"]", "\"questionType\":null"})
    void rejectsUnknownMissingAndMultipleTypes(String field) throws Exception {
        mvc.perform(post("/api/quizzes").header("Idempotency-Key", java.util.UUID.randomUUID()).contentType(MediaType.APPLICATION_JSON)
                        .content(REQUEST.replace("\"questionType\":\"SINGLE_CHOICE\"", field)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void validRequestChecksCatalogButDoesNotCreateFakeRun() throws Exception {
        mvc.perform(post("/api/quizzes").header("Idempotency-Key", java.util.UUID.randomUUID()).contentType(MediaType.APPLICATION_JSON).content(REQUEST))
                .andExpect(status().isNotImplemented())
                .andExpect(jsonPath("$.code").value("GENERATION_NOT_IMPLEMENTED"));
        verify(options).findAll();
        verifyNoInteractions(snapshots);
    }

    @Test
    void dependencyFailureReturnsSafe503ForBothOperations() throws Exception {
        when(options.findAll()).thenThrow(new DataAccessResourceFailureException("secret database details"));
        mvc.perform(get("/api/generation/parameters")).andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("GENERATION_CONFIG_UNAVAILABLE"));
        mvc.perform(post("/api/quizzes").header("Idempotency-Key", java.util.UUID.randomUUID()).contentType(MediaType.APPLICATION_JSON).content(REQUEST))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("GENERATION_CONFIG_UNAVAILABLE"));
    }
}
