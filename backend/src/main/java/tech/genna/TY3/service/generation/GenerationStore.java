package tech.genna.TY3.service.generation;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import tech.genna.TY3.api.model.*;
import tech.genna.TY3.entity.Material;
import tech.genna.TY3.entity.User;
import tech.genna.TY3.repository.MaterialRepository;
import tools.jackson.databind.ObjectMapper;
import java.time.OffsetDateTime;
import java.util.*;

@Repository
@RequiredArgsConstructor
public class GenerationStore {
    private final JdbcTemplate jdbc;
    private final MaterialRepository materials;
    private final ObjectMapper json;
    private final GenerationTimeout timeout;
    private final GenerationParametersService settings;

    public record Job(UUID id, UUID userId, UUID fileId, String format, String materialName,
                      UUID subjectId, String subjectName, GenerationParameters parameters,
                      String status, String errorCode, UUID claimToken, OffsetDateTime createdAt, OffsetDateTime updatedAt,
                      String stage, String detailStage, long version) {}

    @Transactional
    public GenerationJob enqueue(UUID key, GenerationParameters parameters, User user) {
        // Serialize requests per owner, including material deletion. No IO to S3/ML in this transaction.
        jdbc.queryForObject("SELECT id FROM users WHERE id = ? FOR UPDATE", UUID.class, user.getId());
        jdbc.update("UPDATE generation_jobs SET idempotency_key = NULL WHERE user_id = ? "
                + "AND idempotency_key = ? AND created_at <= CURRENT_TIMESTAMP - INTERVAL '24 hours'", user.getId(), key);
        var existing = jdbc.query("SELECT * FROM generation_jobs WHERE user_id = ? AND idempotency_key = ?",
                (rs, n) -> map(rs), user.getId(), key);
        if (!existing.isEmpty()) {
            var job = existing.getFirst();
            if (!job.parameters().equals(parameters))
                throw new ResponseStatusException(HttpStatus.CONFLICT, "IDEMPOTENCY_KEY_REUSED");
            return response(job);
        }
        UUID id = UUID.randomUUID();
        // Validate against the mutable SQL catalogue only for a NEW accepted run.
        // An idempotent replay above retains its original immutable snapshot.
        settings.saveForRun(id, new GenerateQuizRequest().materialId(parameters.materialId())
                .questionCount(QuestionCount.fromValue(parameters.questionCount()))
                .difficulty(parameters.difficulty()).questionType(parameters.questionType()));
        Material material = materials.findByIdAndUserId(parameters.materialId(), user.getId())
                .orElseThrow(GenerationStore::notFound);
        jdbc.update("""
                INSERT INTO generation_jobs(id, user_id, idempotency_key, request_json, material_id,
                    file_id, format, material_name, subject_id, subject_name, status)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 'QUEUED')
                """, id, user.getId(), key, json.writeValueAsString(parameters), material.getId(),
                material.getFileId(), material.getFileType(), material.getTitle(),
                material.getSubject().getId(), material.getSubject().getName());
        return get(id, user);
    }

    @Transactional(readOnly = true)
    public GenerationJob get(UUID id, User user) {
        var jobs = jdbc.query("SELECT * FROM generation_jobs WHERE id = ? AND user_id = ?",
                (rs, n) -> map(rs), id, user.getId());
        if (jobs.isEmpty()) throw notFound();
        return response(jobs.getFirst());
    }

    /** Claim only work which has never started; an unknown external outcome is not retried automatically. */
    public Optional<Job> lockNext() {
        return jdbc.query("SELECT * FROM generation_jobs WHERE status = 'QUEUED' ORDER BY created_at, id "
                + "LIMIT 1 FOR UPDATE SKIP LOCKED", (rs, n) -> map(rs)).stream().findFirst();
    }

    public void expireClaims() {
        jdbc.update("UPDATE generation_jobs SET status = 'FAILED', stage = 'FAILED', detail_stage = NULL, "
                + "error_code = 'GENERATION_INTERRUPTED', state_version = state_version + 1, updated_at = CURRENT_TIMESTAMP "
                + "WHERE status = 'RUNNING' AND lease_until < CURRENT_TIMESTAMP");
    }

    public void expireClaim(UUID id, UUID token) {
        jdbc.update("UPDATE generation_jobs SET status = 'FAILED', stage = 'FAILED', detail_stage = NULL, "
                + "error_code = 'GENERATION_INTERRUPTED', state_version = state_version + 1, updated_at = CURRENT_TIMESTAMP "
                + "WHERE id = ? AND claim_token = ? AND status = 'RUNNING'", id, token);
    }

    public Optional<Job> lock(UUID id) {
        return jdbc.query("SELECT * FROM generation_jobs WHERE id = ? FOR UPDATE",
                (rs, n) -> map(rs), id).stream().findFirst();
    }

    public void running(UUID id, UUID token) {
        jdbc.update("UPDATE generation_jobs SET status = 'RUNNING', claim_token = ?, "
                + "stage = 'PREPARING', detail_stage = 'EXTRACTING', ml_sequence = 0, state_version = state_version + 1, "
                + "lease_until = CURRENT_TIMESTAMP + (? * INTERVAL '1 second'), updated_at = CURRENT_TIMESTAMP WHERE id = ?",
                token, timeout.duration().toSeconds(), id);
    }

    @Transactional
    public void recordProgress(UUID id, UUID token, tech.genna.TY3.ml.GenerationEvent event) {
        if (!id.toString().equals(event.requestId()) || event.stage() == null || event.sequence() < 1) return;
        String stage = switch (event.stage()) {
            case VALIDATING, REVIEWING_CONTENT, VALIDATED -> "VALIDATING";
            default -> "GENERATING";
        };
        jdbc.update("UPDATE generation_jobs SET stage = ?, detail_stage = ?, ml_sequence = ?, "
                + "state_version = state_version + 1, updated_at = CURRENT_TIMESTAMP "
                + "WHERE id = ? AND claim_token = ? AND status = 'RUNNING' AND ml_sequence < ?",
                stage, event.stage().name(), event.sequence(), id, token, event.sequence());
    }

    @Transactional
    public void saving(UUID id, UUID token) {
        jdbc.update("UPDATE generation_jobs SET stage = 'SAVING', detail_stage = NULL, ml_sequence = ?, "
                + "state_version = state_version + 1, updated_at = CURRENT_TIMESTAMP "
                + "WHERE id = ? AND claim_token = ? AND status = 'RUNNING'", Long.MAX_VALUE, id, token);
    }

    public void fail(UUID id, String code) {
        jdbc.update("UPDATE generation_jobs SET status = 'FAILED', stage = 'FAILED', detail_stage = NULL, "
                + "error_code = ?, state_version = state_version + 1, updated_at = CURRENT_TIMESTAMP "
                + "WHERE id = ? AND status IN ('QUEUED', 'RUNNING')", code, id);
    }

    /** A single persistence entry point. The caller holds the job lock and transaction. */
    public void saveQuiz(Job job, MlGenerator.Result result) {
        UUID quizId = job.id();
        var p = job.parameters();
        var summary = new QuizSummary(quizId, p.name(), p.materialId(), job.materialName(),
                job.subjectId(), job.subjectName(), List.of(), QuizKind.TEST, p.difficulty(),
                result.questions().size(), OffsetDateTime.now())
                .format(FileFormat.fromValue(job.format())).questionType(p.questionType());
        jdbc.update("""
                INSERT INTO quizzes(id, job_id, user_id, summary_json, result_json)
                VALUES (?, ?, ?, ?, ?) ON CONFLICT (job_id) DO NOTHING
                """, quizId, job.id(), job.userId(), json.writeValueAsString(summary), json.writeValueAsString(SavedQuiz.from(result, p)));
        jdbc.update("UPDATE generation_jobs SET status = 'COMPLETED', stage = 'COMPLETED', detail_stage = NULL, "
                + "error_code = NULL, state_version = state_version + 1, updated_at = CURRENT_TIMESTAMP WHERE id = ?", job.id());
    }

    @Transactional(readOnly = true)
    public QuizListResponse list(User user) {
        return new QuizListResponse(jdbc.query("SELECT summary_json FROM quizzes WHERE user_id = ? ORDER BY created_at DESC, id DESC",
                (rs, n) -> json.readValue(rs.getString(1), QuizSummary.class), user.getId()));
    }

    private Job map(java.sql.ResultSet rs) throws java.sql.SQLException {
        return new Job(rs.getObject("id", UUID.class), rs.getObject("user_id", UUID.class),
                rs.getObject("file_id", UUID.class), rs.getString("format"), rs.getString("material_name"),
                rs.getObject("subject_id", UUID.class), rs.getString("subject_name"),
                json.readValue(rs.getString("request_json"), GenerationParameters.class), rs.getString("status"),
                rs.getString("error_code"), rs.getObject("claim_token", UUID.class), rs.getObject("created_at", OffsetDateTime.class),
                rs.getObject("updated_at", OffsetDateTime.class), rs.getString("stage"), rs.getString("detail_stage"),
                rs.getLong("state_version"));
    }

    public GenerationJob response(Job job) {
        var response = new GenerationJob(job.id(), job.parameters().materialId(), QuizKind.TEST,
                GenerationJob.StatusEnum.fromValue(job.status()), Math.toIntExact(job.version()), job.createdAt(), job.updatedAt())
                .stage(GenerationJob.StageEnum.fromValue(job.stage()));
        if (job.detailStage() != null) response.detailStage(GenerationJob.DetailStageEnum.fromValue(job.detailStage()));
        if ("COMPLETED".equals(job.status())) response.progress(100);
        if ("COMPLETED".equals(job.status())) response.quizId(job.id());
        if ("FAILED".equals(job.status())) response.error(safeError(job.errorCode()));
        return response;
    }

    static TaskError safeError(String code) {
        String message = switch (code == null ? "" : code) {
            case "DOCUMENT_PROTECTED" -> "Документ защищён. Загрузите копию без защиты.";
            case "DOCUMENT_UNREADABLE" -> "Не удалось прочитать документ. TXT должен быть в UTF-8.";
            case "DOCUMENT_NO_TEXT" -> "В документе нет доступного текста. Для скана требуется распознавание.";
            case "DOCUMENT_TOO_LARGE", "DOCUMENT_TEXT_TOO_LARGE", "DOCUMENT_TOO_MANY_PAGES" -> "Документ превышает допустимые ограничения.";
            case "ML_NOT_CONFIGURED" -> "Сервис генерации ещё не подключён.";
            case "NOT_CONFIGURED" -> "На сервере не настроен ключ сервиса генерации.";
            case "MODEL_TIMEOUT" -> "Модель не ответила вовремя. Попробуйте позже.";
            case "RATE_LIMITED" -> "Достигнут лимит сервиса генерации. Попробуйте позже.";
            case "MODEL_UNAVAILABLE", "ML_UNAVAILABLE" -> "Сервис генерации временно недоступен.";
            case "MODEL_REFUSED" -> "Модель не смогла обработать материал.";
            case "MODEL_OUTPUT_LIMIT" -> "Ответ модели превысил допустимый объём.";
            case "INVALID_MODEL_OUTPUT" -> "Модель вернула некорректный тест.";
            case "QUALITY_REJECTED" -> "Сгенерированный тест не прошёл проверку качества.";
            case "INSUFFICIENT_MATERIAL" -> "В материале недостаточно данных для выбранного теста.";
            case "INVALID_INPUT" -> "Материал или параметры не соответствуют ограничениям генератора.";
            case "CANCELLED", "GENERATION_INTERRUPTED" -> "Генерация была прервана. Запустите новую задачу.";
            case "ML_INVALID_RESULT" -> "Сервис генерации вернул некорректный результат.";
            case "STORAGE_UNAVAILABLE" -> "Не удалось получить исходный файл. Попробуйте позже.";
            default -> "Не удалось выполнить генерацию. Попробуйте позже.";
        };
        Set<String> known = new HashSet<>(Set.of("DOCUMENT_PROTECTED", "DOCUMENT_UNREADABLE", "DOCUMENT_NO_TEXT",
                "DOCUMENT_TOO_LARGE", "DOCUMENT_TEXT_TOO_LARGE", "DOCUMENT_TOO_MANY_PAGES", "ML_NOT_CONFIGURED",
                "ML_INVALID_RESULT", "STORAGE_UNAVAILABLE", "ML_UNAVAILABLE", "GENERATION_FAILED", "GENERATION_INTERRUPTED"));
        for (var value : tech.genna.TY3.ml.GenerationFailure.Code.values()) known.add(value.name());
        String safeCode = code != null && known.contains(code) ? code : "GENERATION_FAILED";
        return new TaskError(safeCode, message, Set.of("STORAGE_UNAVAILABLE", "ML_UNAVAILABLE", "GENERATION_FAILED",
                "MODEL_TIMEOUT", "MODEL_UNAVAILABLE", "RATE_LIMITED", "GENERATION_INTERRUPTED", "CANCELLED").contains(safeCode));
    }

    private static ResponseStatusException notFound() {
        return new ResponseStatusException(HttpStatus.NOT_FOUND, "NOT_FOUND");
    }
}
