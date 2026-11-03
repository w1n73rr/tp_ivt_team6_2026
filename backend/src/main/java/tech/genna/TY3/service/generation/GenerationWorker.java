package tech.genna.TY3.service.generation;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.core.task.TaskRejectedException;
import tech.genna.TY3.service.file.FileStorageService;
import java.util.UUID;

@Component
@RequiredArgsConstructor
@Slf4j
public class GenerationWorker {
    private final GenerationStore store;
    private final TransactionTemplate transactions;
    private final FileStorageService storage;
    private final DocumentTextExtractor extractor;
    private final MlGenerator ml;
    private final MlResultValidator validator;
    private final ThreadPoolTaskExecutor generationExecutor;
    private final GenerationProgress progress;
    private final GenerationWatchdog watchdog;

    private record Claim(GenerationStore.Job job, UUID token) {}

    @Scheduled(fixedDelayString = "${generation.worker-delay-ms:1000}", initialDelayString = "${generation.worker-delay-ms:1000}")
    public void tick() {
        try { generationExecutor.execute(this::runNext); }
        catch (TaskRejectedException ignored) { /* Queue stays in PostgreSQL while all workers are busy. */ }
    }

    private void runNext() {
        Claim claim;
        try {
            claim = transactions.execute(tx -> {
                return store.lockNext().map(job -> {
                    UUID token = UUID.randomUUID();
                    store.running(job.id(), token);
                    return new Claim(job, token);
                }).orElse(null);
            });
        } catch (RuntimeException e) {
            log.warn("Unable to claim generation job", e);
            return;
        }
        if (claim == null) return;
        try (var run = watchdog.track(claim.job().id(), claim.token())) {
            processClaim(claim, run);
        }
    }

    private void processClaim(Claim claim, GenerationWatchdog.Run run) {
        var job = claim.job();
        MlGenerator.Result result = null;
        String error = null;
        try {
            String text;
            try (var input = storage.readFile(job.fileId(), job.userId(), FileStorageService.FileScope.MATERIALS)) {
                run.abortReadingWith(input::abort);
                try { text = extractor.extract(input, job.format()); }
                finally {
                    if (run.expired()) input.abort();
                    run.abortReadingWith(null);
                }
            } catch (GenerationFailure e) { throw e; }
            catch (Exception e) { throw new GenerationFailure("STORAGE_UNAVAILABLE"); }
            if (run.expired()) return;
            try (var channel = progress.open(job.id(), claim.token())) {
                try { result = ml.generate(job.id(), text, job.parameters(), channel); }
                catch (GenerationFailure e) { throw e; }
                catch (Exception e) { throw new GenerationFailure("ML_UNAVAILABLE"); }
                validator.validate(job.id(), job.parameters(), result);
            }
            if (!run.expired()) store.saving(job.id(), claim.token());
        } catch (GenerationFailure e) { error = e.code(); }
        catch (RuntimeException e) { error = "GENERATION_FAILED"; }
        if (run.expired()) return; // The watchdog persists failure; never save a late result.
        var completedResult = result;
        var failureCode = error;
        try {
            transactions.executeWithoutResult(tx -> store.lock(job.id()).ifPresent(current -> {
                // A stale worker cannot overwrite a newer claim or a terminal result.
                if (!"RUNNING".equals(current.status()) || !claim.token().equals(current.claimToken())) return;
                if (run.expired()) { store.fail(job.id(), "GENERATION_INTERRUPTED"); return; }
                if (failureCode != null) store.fail(job.id(), failureCode);
                else store.saveQuiz(current, completedResult);
            }));
        } catch (RuntimeException e) {
            // Do not repeat a possibly completed provider request. Expiry marks interrupted work as FAILED.
            log.warn("Unable to commit generation result; task will expire as interrupted", e);
        }
    }

    /** Internal adapter entry point; never expose it as an unauthenticated public callback. */
    public void accept(UUID jobId, MlGenerator.Result result) {
        transactions.executeWithoutResult(tx -> {
            var job = store.lock(jobId).orElseThrow(() -> new IllegalArgumentException("Unknown job"));
            if ("COMPLETED".equals(job.status()) || "FAILED".equals(job.status())) return;
            validator.validate(jobId, job.parameters(), result);
            store.saveQuiz(job, result);
        });
    }
}
