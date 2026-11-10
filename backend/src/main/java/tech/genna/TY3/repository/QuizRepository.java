package tech.genna.TY3.repository;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import tech.genna.TY3.api.model.QuizSummary;
import tech.genna.TY3.service.generation.GenerationParameters;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** CRUD over the existing GenerationStore aggregate. Creation belongs exclusively to GenerationStore.saveQuiz. */
@Repository
@RequiredArgsConstructor
public class QuizRepository {
    private final JdbcTemplate jdbc;
    private final ObjectMapper json;
    public record StoredQuiz(QuizSummary summary, GenerationParameters parameters, JsonNode result) {}

    public void lockOwner(UUID ownerId) {
        jdbc.queryForObject("SELECT id FROM users WHERE id = ? FOR UPDATE", UUID.class, ownerId);
    }

    public List<QuizSummary> listOwned(UUID ownerId) {
        return jdbc.query("SELECT summary_json FROM quizzes WHERE user_id = ? ORDER BY created_at DESC, id DESC",
                (rs, row) -> json.readValue(rs.getString(1), QuizSummary.class), ownerId);
    }

    public Optional<StoredQuiz> findOwned(UUID quizId, UUID ownerId) {
        // One SQL statement supplies a consistent snapshot even if DELETE runs concurrently.
        return jdbc.query("""
                SELECT q.summary_json, q.result_json, j.request_json
                FROM quizzes q JOIN generation_jobs j ON j.id = q.job_id AND j.user_id = q.user_id
                WHERE q.id = ? AND q.user_id = ?
                """, (rs, row) -> new StoredQuiz(json.readValue(rs.getString("summary_json"), QuizSummary.class),
                json.readValue(rs.getString("request_json"), GenerationParameters.class),
                json.readTree(rs.getString("result_json"))), quizId, ownerId).stream().findFirst();
    }

    public boolean nameExists(UUID ownerId, String name, UUID excludedId) {
        return Boolean.TRUE.equals(jdbc.queryForObject("""
                SELECT EXISTS(SELECT 1 FROM quizzes
                    WHERE user_id = ? AND summary_json::jsonb->>'name' = ? AND id <> ?)
                """, Boolean.class, ownerId, name, excludedId));
    }

    public int renameOwned(UUID quizId, UUID ownerId, String name) {
        // Update only the public name. All private question/answer IDs and settings remain byte-for-byte unchanged.
        return jdbc.update("""
                UPDATE quizzes SET summary_json = jsonb_set(summary_json::jsonb, '{name}', to_jsonb(?::text))::text
                WHERE id = ? AND user_id = ?
                """, name, quizId, ownerId);
    }

    public int deleteOwned(UUID quizId, UUID ownerId) {
        return jdbc.update("DELETE FROM quizzes WHERE id = ? AND user_id = ?", quizId, ownerId);
    }
}
