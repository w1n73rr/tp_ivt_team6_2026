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
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class GenerationWorkerTest {
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
