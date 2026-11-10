package tech.genna.TY3.service.generation;

import org.junit.jupiter.api.Test;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.TransactionStatus;
import software.amazon.awssdk.core.ResponseInputStream;
import tech.genna.TY3.service.file.FileStorageService;
import tech.genna.TY3.api.model.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class GenerationWorkerTest {
    @Test void failedSaveRecordsFailureInAnotherTransaction() throws Exception {
        verifyCommitFailureRecovery("RUNNING", false, false);
    }

    @Test void ambiguousCommitDoesNotOverwriteCompletedOrNewClaim() throws Exception {
        verifyCommitFailureRecovery("COMPLETED", false, false);
        verifyCommitFailureRecovery("FAILED", false, false);
        verifyCommitFailureRecovery("RUNNING", true, false);
    }

    @Test void unavailableDatabaseLeavesRecoveryToExpiry() throws Exception {
        verifyCommitFailureRecovery("RUNNING", false, true);
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private void verifyCommitFailureRecovery(String recoveredStatus, boolean differentClaim, boolean unavailable) throws Exception {
        var store = mock(GenerationStore.class);
        var tx = mock(TransactionTemplate.class);
        when(tx.execute(any())).thenAnswer(call -> ((TransactionCallback<?>) call.getArgument(0))
                .doInTransaction(mock(TransactionStatus.class)));
        var txCount = new AtomicInteger();
        doAnswer(call -> {
            txCount.incrementAndGet();
            ((Consumer<TransactionStatus>) call.getArgument(0)).accept(mock(TransactionStatus.class));
            return null;
        }).when(tx).executeWithoutResult(any());
        var token = new AtomicReference<UUID>();
        doAnswer(call -> { token.set(call.getArgument(1)); return null; }).when(store).running(any(), any());
        UUID id = UUID.randomUUID();
        var queued = mock(GenerationStore.Job.class);
        when(queued.id()).thenReturn(id);
        when(queued.fileId()).thenReturn(UUID.randomUUID());
        when(queued.userId()).thenReturn(UUID.randomUUID());
        when(queued.format()).thenReturn("TXT");
        when(store.lockNext()).thenReturn(Optional.of(queued));
        var locked = mock(GenerationStore.Job.class);
        when(locked.status()).thenAnswer(call -> txCount.get() == 1 ? "RUNNING" : recoveredStatus);
        when(locked.claimToken()).thenAnswer(call -> txCount.get() > 1 && differentClaim ? UUID.randomUUID() : token.get());
        when(store.lock(id)).thenAnswer(call -> {
            if (txCount.get() > 1 && unavailable) throw new IllegalStateException("Database unavailable");
            return Optional.of(locked);
        });
        doThrow(new IllegalStateException("Save failed")).when(store).saveQuiz(any(), any());
        var storage = mock(FileStorageService.class);
        when(storage.readFile(any(), any(), any())).thenReturn(mock(ResponseInputStream.class));
        var extractor = mock(DocumentTextExtractor.class);
        when(extractor.extract(any(), anyString())).thenReturn("Lecture");
        var ml = mock(MlGenerator.class);
        when(ml.generate(any(), anyString(), any(), any())).thenReturn(new MlGenerator.Result(id, List.of()));
        var pool = mock(ThreadPoolTaskExecutor.class);
        doAnswer(call -> { ((Runnable) call.getArgument(0)).run(); return null; }).when(pool).execute(any(Runnable.class));
        var watchdog = new GenerationWatchdog(store, Duration.ofMinutes(6), () -> 0);
        try {
            var worker = new GenerationWorker(store, tx, storage, extractor, ml, mock(MlResultValidator.class),
                    pool, new GenerationProgress(store), watchdog);
            assertDoesNotThrow(worker::tick);
            assertEquals(2, txCount.get());
            verify(store, times(1)).saveQuiz(any(), any());
            verify(ml, times(1)).generate(any(), anyString(), any(), any());
            if ("RUNNING".equals(recoveredStatus) && !differentClaim && !unavailable)
                verify(store).fail(id, "GENERATION_FAILED");
            else verify(store, never()).fail(any(), any());
        } finally { watchdog.stop(); }
    }

    @Test @SuppressWarnings({"unchecked", "rawtypes"})
    void resultReturningAfterWholeJobDeadlineIsNeverSaved() throws Exception {
        var store = mock(GenerationStore.class);
        var tx = mock(TransactionTemplate.class);
        when(tx.execute(any())).thenAnswer(call -> ((TransactionCallback<?>) call.getArgument(0))
                .doInTransaction(mock(TransactionStatus.class)));
        var storage = mock(FileStorageService.class);
        var stream = mock(ResponseInputStream.class);
        when(storage.readFile(any(), any(), any())).thenReturn(stream);
        var extractor = mock(DocumentTextExtractor.class);
        when(extractor.extract(any(), any())).thenReturn("Prepared lecture");
        var ml = mock(MlGenerator.class);
        var validator = mock(MlResultValidator.class);
        var time = new AtomicLong();
        var watchdog = new GenerationWatchdog(store, Duration.ofSeconds(1), time::get);
        var pool = new ThreadPoolTaskExecutor();
        pool.setCorePoolSize(1); pool.setMaxPoolSize(1); pool.setQueueCapacity(0); pool.initialize();
        var enteredModel = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        var modelFinished = new CountDownLatch(1);
        UUID id = UUID.randomUUID();
        var parameters = new GenerationParameters(UUID.randomUUID(), "Quiz", 5, Difficulty.MEDIUM,
                QuestionType.SINGLE_CHOICE, "ru");
        var job = new GenerationStore.Job(id, UUID.randomUUID(), UUID.randomUUID(), "TXT", "Lecture",
                UUID.randomUUID(), "Subject", parameters, "QUEUED", null, null,
                OffsetDateTime.now(), OffsetDateTime.now(), "PREPARING", null, 0);
        when(store.lockNext()).thenReturn(Optional.of(job));
        when(ml.generate(eq(id), anyString(), eq(parameters), any())).thenAnswer(call -> {
            enteredModel.countDown();
            boolean done = false;
            while (!done) try { release.await(); done = true; } catch (InterruptedException ignored) { }
            modelFinished.countDown();
            return new MlGenerator.Result(id, List.of());
        });
        var worker = new GenerationWorker(store, tx, storage, extractor, ml, validator, pool,
                new GenerationProgress(store), watchdog);
        try {
            worker.tick();
            assertTrue(enteredModel.await(2, TimeUnit.SECONDS));
            time.set(Duration.ofSeconds(2).toNanos());
            watchdog.check();
            verify(store).expireClaim(eq(id), any());
            release.countDown();
            assertTrue(modelFinished.await(2, TimeUnit.SECONDS));
            pool.setWaitForTasksToCompleteOnShutdown(true); pool.setAwaitTerminationSeconds(2); pool.shutdown();
            verify(store, never()).saveQuiz(any(), any());
            verify(store, never()).saving(any(), any());
            verify(tx, never()).executeWithoutResult(any());
        } finally { release.countDown(); pool.shutdown(); watchdog.stop(); }
    }
}
