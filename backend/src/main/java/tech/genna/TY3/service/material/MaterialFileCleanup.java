package tech.genna.TY3.service.material;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import tech.genna.TY3.service.file.FileStorageService;
import java.util.UUID;

/** Durable compensation for failed uploads and committed material deletions. */
@Service
@RequiredArgsConstructor
@Slf4j
public class MaterialFileCleanup {
    private final JdbcTemplate jdbc;
    private final TransactionTemplate transactions;
    private final FileStorageService storage;

    public void reserve(UUID fileId, UUID userId) {
        transactions.executeWithoutResult(status -> jdbc.update("""
                INSERT INTO material_file_cleanup(file_id, user_id, available_at)
                VALUES (?, ?, CURRENT_TIMESTAMP + INTERVAL '10 minutes')
                """, fileId, userId));
    }

    /** Held across upload and DB commit, so the worker cannot delete an in-flight upload. */
    public void lockReservation(UUID fileId) {
        jdbc.queryForObject("SELECT file_id FROM material_file_cleanup WHERE file_id = ? FOR UPDATE",
                UUID.class, fileId);
    }

    public void cancel(UUID fileId) {
        jdbc.update("DELETE FROM material_file_cleanup WHERE file_id = ?", fileId);
    }

    public void enqueue(UUID fileId, UUID userId) {
        jdbc.update("INSERT INTO material_file_cleanup(file_id, user_id) VALUES (?, ?)", fileId, userId);
    }

    @Scheduled(fixedDelayString = "${materials.cleanup-delay-ms:60000}")
    public void clean() {
        cleanFile(null);
    }

    /** Immediate deletion must not wait for the entire cleanup backlog. */
    public void cleanFile(UUID fileId) {
        try {
            transactions.executeWithoutResult(status -> {
                String query = """
                        SELECT file_id, user_id FROM material_file_cleanup
                        WHERE available_at <= CURRENT_TIMESTAMP
                        """ + (fileId == null ? "" : " AND file_id = ? ") + """
                        ORDER BY available_at LIMIT 50 FOR UPDATE SKIP LOCKED
                        """;
                var pending = jdbc.query(query,
                        (rs, row) -> new Pending(rs.getObject(1, UUID.class), rs.getObject(2, UUID.class)),
                        fileId == null ? new Object[0] : new Object[]{fileId});
                for (var item : pending) {
                    try {
                        storage.deleteFile(item.fileId(), item.userId(), FileStorageService.FileScope.MATERIALS);
                        cancel(item.fileId());
                    } catch (tech.genna.TY3.model.exception.FileStorageException e) {
                        log.warn("Material file cleanup will be retried for {}", item.fileId(), e);
                        jdbc.update("""
                                UPDATE material_file_cleanup
                                SET available_at = CURRENT_TIMESTAMP + INTERVAL '1 minute'
                                WHERE file_id = ?
                                """, item.fileId());
                        break;
                    }
                }
            });
        } catch (RuntimeException e) {
            log.error("Material file cleanup failed; durable tasks will be retried", e);
        }
    }

    private record Pending(UUID fileId, UUID userId) {}
}
