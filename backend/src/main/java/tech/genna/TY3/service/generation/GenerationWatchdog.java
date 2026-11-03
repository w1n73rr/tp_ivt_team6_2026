package tech.genna.TY3.service.generation;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import java.time.Duration;
import java.util.UUID;
import java.util.Map;
import java.util.ArrayList;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.LongSupplier;

/** Runs independently of the bounded generation pool and other scheduled DB work. */
@Component
@Slf4j
public class GenerationWatchdog {
    private final GenerationStore store;
    private final Duration timeout;
    private final LongSupplier nanoTime;
    private final Map<UUID, Run> active = new ConcurrentHashMap<>();
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(
            Thread.ofPlatform().daemon(true).name("generation-watchdog").factory());

    @Autowired
    public GenerationWatchdog(GenerationStore store, GenerationTimeout timeout) {
        this(store, timeout.duration(), System::nanoTime);
    }

    GenerationWatchdog(GenerationStore store, Duration timeout, LongSupplier nanoTime) {
        this.store = store; this.timeout = timeout; this.nanoTime = nanoTime;
    }

    @PostConstruct public void start() {
        scheduler.scheduleWithFixedDelay(this::check, 1, 1, TimeUnit.SECONDS);
    }

    public Run track(UUID id, UUID token) {
        var run = new Run(id, token, Thread.currentThread(), nanoTime.getAsLong());
        if (active.putIfAbsent(id, run) != null) throw new IllegalStateException("Job already tracked");
        return run;
    }

    void check() {
        var expired = new ArrayList<Run>();
        active.values().forEach(run -> {
            if (!run.expired() || !run.finished.compareAndSet(false, true)) return;
            run.cancelled.set(true);
            run.thread.interrupt();
            run.abortRead();
            expired.add(run);
        });
        // Request cancellation of every run before any potentially slow SQL operation.
        expired.forEach(run -> {
            try { store.expireClaim(run.id, run.token); }
            catch (RuntimeException ignored) { log.warn("Unable to expire active generation job {}", run.id); }
        });
        // Also recovers claims left behind by another/crashed process; never needs a worker slot.
        try { store.expireClaims(); }
        catch (RuntimeException ignored) { log.warn("Unable to expire generation claims"); }
    }

    public final class Run implements AutoCloseable {
        private final UUID id, token;
        private final Thread thread;
        private final long started;
        private final AtomicBoolean finished = new AtomicBoolean();
        private final AtomicBoolean cancelled = new AtomicBoolean();
        private final AtomicReference<Runnable> abort = new AtomicReference<>();

        private Run(UUID id, UUID token, Thread thread, long started) {
            this.id = id; this.token = token; this.thread = thread; this.started = started;
        }

        public boolean expired() {
            return cancelled.get() || nanoTime.getAsLong() - started >= timeout.toNanos();
        }

        /** S3 abort does not drain an unread body, unlike close of a pooled response. */
        public void abortReadingWith(Runnable action) {
            abort.set(action);
            if (cancelled.get()) abortRead();
        }

        private void abortRead() {
            Runnable action = abort.getAndSet(null);
            if (action != null) try { action.run(); }
            catch (RuntimeException ignored) { log.warn("Unable to abort expired generation file read {}", id); }
        }

        @Override public void close() {
            finished.set(true);
            abort.set(null);
            active.remove(id, this);
        }
    }

    @PreDestroy public void stop() { scheduler.shutdownNow(); }
}
