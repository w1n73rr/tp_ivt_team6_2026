package tech.genna.TY3.repository;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import tech.genna.TY3.api.model.Difficulty;
import tech.genna.TY3.api.model.QuestionType;
import tech.genna.TY3.service.generation.SelectedGenerationSettings;

import java.util.Optional;
import java.util.UUID;

@Repository
@RequiredArgsConstructor
public class GenerationSettingsSnapshotRepository {
    private final JdbcTemplate jdbc;

    // Insert only. A retry with the same job ID must not overwrite the original settings.
    public void insert(UUID jobId, SelectedGenerationSettings settings) {
        jdbc.update("""
                INSERT INTO generation_settings_snapshots(job_id, question_count, difficulty, question_type)
                VALUES (?, ?, ?, ?)
                """, jobId, settings.questionCount(), settings.difficulty().getValue(), settings.questionType().getValue());
    }

    public Optional<SelectedGenerationSettings> findByJobId(UUID jobId) {
        return jdbc.query("""
                SELECT question_count, difficulty, question_type
                FROM generation_settings_snapshots WHERE job_id = ?
                """, (rs, row) -> new SelectedGenerationSettings(rs.getInt("question_count"),
                Difficulty.fromValue(rs.getString("difficulty")),
                QuestionType.fromValue(rs.getString("question_type"))), jobId).stream().findFirst();
    }
}
