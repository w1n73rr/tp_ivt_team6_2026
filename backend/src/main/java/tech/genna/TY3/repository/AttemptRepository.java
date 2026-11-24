package tech.genna.TY3.repository;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import tech.genna.TY3.api.model.AttemptCompletion;
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
        return jdbc.query("""
                SELECT a.id FROM attempt_start_requests r
                JOIN attempts a ON a.id=r.attempt_id AND a.user_id=r.user_id
                WHERE r.user_id=? AND r.idempotency_key=?
                """,
                (rs,n) -> rs.getObject(1, UUID.class), owner, key).stream().findFirst();
    }
    public Optional<Attempt> active(UUID quizId, UUID owner) {
        return jdbc.query("SELECT id FROM attempts WHERE quiz_id=? AND user_id=? AND status='IN_PROGRESS' FOR UPDATE",
                (rs,n) -> rs.getObject(1, UUID.class),quizId,owner).stream().findFirst()
                .flatMap(id -> find(id,owner,false));
    }
    public void bindStartKey(UUID owner, String key, UUID attemptId) {
        jdbc.update("INSERT INTO attempt_start_requests(user_id,idempotency_key,attempt_id) VALUES (?,?,?)",
                owner,key,attemptId);
    }
    public void create(Attempt a, String key) {
        jdbc.update("INSERT INTO attempts(id,user_id,quiz_id,idempotency_key,quiz_json,questions_json,started_at) VALUES (?,?,?,?,?,?,?)",
                a.id(), a.owner(), a.quizId(), key, a.quiz().toString(), a.questions().toString(), a.startedAt());
    }
    public void answers(UUID id, UUID owner, JsonNode answers) {
        jdbc.update("UPDATE attempts SET answers_json=? WHERE id=? AND user_id=?", answers.toString(), id, owner);
    }
    public AttemptCompletion completion(UUID id, UUID owner) {
        return jdbc.queryForObject("SELECT result_json FROM attempts WHERE id=? AND user_id=?",
                (rs,n) -> json.readValue(rs.getString(1),AttemptCompletion.class),id,owner);
    }
    public void complete(UUID id, UUID owner, AttemptCompletion result) {
        int changed=jdbc.update("UPDATE attempts SET result_json=?, completed_at=?, status='COMPLETED' WHERE id=? AND user_id=? AND status='IN_PROGRESS'",
                json.writeValueAsString(result),result.getCompletedAt(),id,owner);
        if(changed!=1) throw new org.springframework.dao.ConcurrencyFailureException("ATTEMPT_CLOSED");
    }
}
