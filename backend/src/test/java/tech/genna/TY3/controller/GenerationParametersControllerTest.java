package tech.genna.TY3.controller;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessResourceFailureException;
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

    @BeforeEach void setUp() {
        options = mock(GenerationOptionsRepository.class);
        when(options.findAll()).thenReturn(GenerationParametersServiceTest.initialOptions());
        var service = new GenerationParametersService(options, mock(GenerationSettingsSnapshotRepository.class));
        mvc = MockMvcBuilders.standaloneSetup(new GenerationParametersController(service))
                .setControllerAdvice(new GlobalExceptionHandler()).build();
    }

    @Test void responseMatchesGeneratedContractAndIsNotCached() throws Exception {
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

    @Test void dependencyFailureReturnsSafe503() throws Exception {
        when(options.findAll()).thenThrow(new DataAccessResourceFailureException("secret database details"));
        mvc.perform(get("/api/generation/parameters")).andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("GENERATION_CONFIG_UNAVAILABLE"));
    }
}
