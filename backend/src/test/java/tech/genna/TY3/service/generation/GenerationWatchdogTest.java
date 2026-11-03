package tech.genna.TY3.service.generation;

import org.junit.jupiter.api.Test;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.core.task.TaskRejectedException;
import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class GenerationWatchdogTest {
    @Test void expiresClaimsEvenWhenEveryWorkerIsBusyAndParserIgnoresInterruption() throws Exception {
        var store = mock(GenerationStore.class);
        var time = new AtomicLong();
        var watchdog = new GenerationWatchdog(store, Duration.ofSeconds(1), time::get);
        var pool = new ThreadPoolTaskExecutor();
        pool.setCorePoolSize(2); pool.setMaxPoolSize(2); pool.setQueueCapacity(0); pool.initialize();
        var ready = new CountDownLatch(2);
        var release = new CountDownLatch(1);
        var interrupted = new CountDownLatch(2);
        Runnable[] aborts = {mock(Runnable.class), mock(Runnable.class)};
        UUID[] jobs = {UUID.randomUUID(), UUID.randomUUID()};
        UUID[] tokens = {UUID.randomUUID(), UUID.randomUUID()};
        try {
            for (int i = 0; i < 2; i++) {
                int index = i;
                pool.execute(() -> {
                    try (var run = watchdog.track(jobs[index], tokens[index])) {
                        run.abortReadingWith(aborts[index]);
                        ready.countDown();
                        boolean done = false;
                        while (!done) try { release.await(); done = true; }
                        catch (InterruptedException e) { interrupted.countDown(); }
                    }
                });
            }
            assertTrue(ready.await(2, TimeUnit.SECONDS));
            assertThrows(TaskRejectedException.class, () -> pool.execute(() -> {}));
            time.set(Duration.ofSeconds(2).toNanos());
            watchdog.check();
            assertTrue(interrupted.await(2, TimeUnit.SECONDS));
            for (int i = 0; i < 2; i++) {
                verify(store).expireClaim(jobs[i], tokens[i]);
                verify(aborts[i]).run();
            }
            verify(store).expireClaims();
        } finally {
            release.countDown(); pool.shutdown(); watchdog.stop();
        }
    }

    @Test void reapsOrphanedClaimsWithoutAnyActiveLocalWorker() {
        var store = mock(GenerationStore.class);
        var watchdog = new GenerationWatchdog(store, Duration.ofSeconds(1), () -> 0);
        try { watchdog.check(); verify(store).expireClaims(); }
        finally { watchdog.stop(); }
    }

    @Test void completedRunIsNotInterruptedOrExpiredLater() throws Exception {
        var store = mock(GenerationStore.class);
        var time = new AtomicLong();
        var watchdog = new GenerationWatchdog(store, Duration.ofSeconds(1), time::get);
        UUID id = UUID.randomUUID(), token = UUID.randomUUID();
        var thread = Thread.ofVirtual().start(() -> { try (var run = watchdog.track(id, token)) { } });
        thread.join(2000);
        assertFalse(thread.isAlive());
        time.set(Duration.ofSeconds(2).toNanos());
        try { watchdog.check(); verify(store, never()).expireClaim(any(), any()); }
        finally { watchdog.stop(); }
    }

    @Test void deadlineDefaultsIncludeWholeJobAndRejectInvalidConfiguration() {
        assertEquals(Duration.ofSeconds(360), new GenerationTimeout(0, 240).duration());
        assertEquals(Duration.ofSeconds(15), new GenerationTimeout(15, 240).duration());
        assertThrows(IllegalArgumentException.class, () -> new GenerationTimeout(-1, 240));
        assertThrows(IllegalArgumentException.class, () -> new GenerationTimeout(3601, 240));
    }
}
