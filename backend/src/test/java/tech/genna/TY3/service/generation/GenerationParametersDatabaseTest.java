package tech.genna.TY3.service.generation;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import tech.genna.TY3.service.file.FileStorageService;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import tech.genna.TY3.api.model.*;
import tech.genna.TY3.repository.GenerationSettingsSnapshotRepository;
import tools.jackson.databind.ObjectMapper;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/** Migrations and JDBC on disposable PostgreSQL supplied through TEST_DATABASE_*; S3 is isolated. */
@SpringBootTest(properties = "spring.config.import=")
@ActiveProfiles("test")
@Transactional
class GenerationParametersDatabaseTest {
    @MockitoBean FileStorageService fileStorageService;
    @Autowired GenerationParametersService service;
    @Autowired GenerationSettingsSnapshotRepository snapshots;
    @Autowired JdbcTemplate jdbc;
    @Autowired ObjectMapper mapper;

    @Test
    void rejectsFractionalQuestionCountInsteadOfRoundingIt() {
        assertThrows(tools.jackson.core.JacksonException.class, () -> mapper.readValue("""
                {"materialId":"00000000-0000-0000-0000-000000000001",
                 "questionCount":5.5,"difficulty":"MEDIUM","questionType":"SINGLE_CHOICE"}
                """, GenerateQuizRequest.class));
    }

    @Test
    void snapshotSurvivesDefaultAndAvailabilityChange() {
        var jobId = UUID.randomUUID();
        var request = new GenerateQuizRequest(UUID.randomUUID(), QuestionCount.NUMBER_10, Difficulty.MEDIUM, QuestionType.SINGLE_CHOICE);
        var original = service.saveForRun(jobId, request);

        jdbc.update("UPDATE generation_options SET default_slot = NULL WHERE category = 'QUESTION_COUNT'");
        jdbc.update("UPDATE generation_options SET default_slot = 1 WHERE category = 'QUESTION_COUNT' AND option_value = '5'");
        jdbc.update("UPDATE generation_options SET available = FALSE WHERE category = 'QUESTION_COUNT' AND option_value = '10'");

        assertEquals(5, service.getParameters().getDefaults().getQuestionCount().getValue());
        assertEquals(original, snapshots.findByJobId(jobId).orElseThrow());
        assertEquals(400, assertThrows(ResponseStatusException.class, () -> service.validate(request)).getStatusCode().value());
        assertEquals(1, jdbc.queryForObject("SELECT count(*) FROM generation_settings_snapshots WHERE job_id = ?", Integer.class, jobId));
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void cannotReplaceExistingSnapshot() {
        var jobId = UUID.randomUUID();
        var original = service.saveForRun(jobId,
                new GenerateQuizRequest(UUID.randomUUID(), QuestionCount.NUMBER_5, Difficulty.EASY, QuestionType.TRUE_FALSE));
        try {
            // A rejected insert aborts its PostgreSQL transaction, so check the committed
            // original in a separate statement without a surrounding test transaction.
            assertThrows(org.springframework.dao.DuplicateKeyException.class, () -> snapshots.insert(jobId,
                    new SelectedGenerationSettings(10, Difficulty.HARD, QuestionType.MULTIPLE_CHOICE)));
            assertEquals(original, snapshots.findByJobId(jobId).orElseThrow());
        } finally {
            jdbc.update("DELETE FROM generation_settings_snapshots WHERE job_id = ?", jobId);
        }
    }
}
