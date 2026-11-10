package tech.genna.TY3.service.generation;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import tech.genna.TY3.ml.GenerationEvent;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

/** The synchronous ML callback only offers to a bounded queue; it never touches SQL or sockets. */
@Component
@RequiredArgsConstructor
@Slf4j
public class GenerationProgress {
    private final GenerationStore store;
    private final Set<Channel> channels = ConcurrentHashMap.newKeySet();

    public Channel open(UUID jobId, UUID token) {
        var channel = new Channel(jobId, token);
        channels.add(channel);
        return channel;
    }

    @Scheduled(fixedDelay = 100)
    public void flush() { channels.forEach(Channel::drain); }

    public final class Channel implements Consumer<GenerationEvent>, AutoCloseable {
        private final UUID jobId;
        private final UUID token;
        private final ArrayBlockingQueue<GenerationEvent> events = new ArrayBlockingQueue<>(32);
        private final AtomicBoolean closed = new AtomicBoolean();
        private Channel(UUID jobId, UUID token) { this.jobId = jobId; this.token = token; }
        @Override public void accept(GenerationEvent event) {
            if (event != null && !closed.get() && jobId.toString().equals(event.requestId())) {
                if (!events.offer(event)) log.warn("Generation progress queue is full for job {}", jobId);
            }
        }
        private synchronized void drain() {
            for (GenerationEvent event; (event = events.poll()) != null;) {
                try { store.recordProgress(jobId, token, event); }
                catch (RuntimeException e) { log.warn("Unable to persist generation progress for job {}", jobId); }
            }
        }
        @Override public void close() {
            closed.set(true);
            drain();
            channels.remove(this);
        }
    }
}
