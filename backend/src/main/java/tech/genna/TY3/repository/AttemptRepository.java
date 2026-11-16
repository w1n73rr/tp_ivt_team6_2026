package tech.genna.TY3.repository;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import java.time.OffsetDateTime;
import java.util.*;

@Repository
@RequiredArgsConstructor
public class AttemptRepository {
    private final JdbcTemplate jdbc;
    private final ObjectMapper json;
    public record Attempt(UUID id, UUID owner, UUID quizId, JsonNode quiz, JsonNode questions,
                          JsonNode answers, String status, OffsetDateTime startedAt) {}
    public Optional<Attempt> find(UUID id, UUID owner, boolean lock) {
        return jdbc.query("SELECT * FROM attempts WHERE id=? AND user_id=?" + (lock ? " FOR UPDATE" : ""),
                (rs, n) -> new Attempt(rs.getObject("id", UUID.class), owner, rs.getObject("quiz_id", UUID.class),
                        json.readTree(rs.getString("quiz_json")), json.readTree(rs.getString("questions_json")),
                        json.readTree(rs.getString("answers_json")), rs.getString("status"),
                        rs.getObject("started_at", OffsetDateTime.class)), id, owner).stream().findFirst();
    }
    public Optional<UUID> replay(UUID owner, String key) {
        return jdbc.query("SELECT id FROM attempts WHERE user_id=? AND idempotency_key=?",
                (rs,n) -> rs.getObject(1, UUID.class), owner, key).stream().findFirst();
    }
    public void create(Attempt a, String key) {
        jdbc.update("INSERT INTO attempts(id,user_id,quiz_id,idempotency_key,quiz_json,questions_json,started_at) VALUES (?,?,?,?,?,?,?)",
                a.id(), a.owner(), a.quizId(), key, a.quiz().toString(), a.questions().toString(), a.startedAt());
    }
    public void answers(UUID id, UUID owner, JsonNode answers) {
        jdbc.update("UPDATE attempts SET answers_json=? WHERE id=? AND user_id=?", answers.toString(), id, owner);
    }
}
