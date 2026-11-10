package tech.genna.TY3.service.generation;

import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.*;
import org.springframework.web.socket.handler.ConcurrentWebSocketSessionDecorator;
import org.springframework.web.socket.handler.TextWebSocketHandler;
import tech.genna.TY3.entity.User;
import tech.genna.TY3.service.jwt.JwtService;
import tech.genna.TY3.service.user.CustomUserDetailsService;
import tools.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

/** Authenticated owner-only snapshots, delivered independently from ML callbacks and threads. */
@Component
@RequiredArgsConstructor
public class GenerationWebSocket extends TextWebSocketHandler {
    private final GenerationStore store;
    private final JwtService jwt;
    private final CustomUserDetailsService users;
    private final ObjectMapper json;
    private final Map<String, Subscription> sessions = new ConcurrentHashMap<>();
    private final ExecutorService delivery = new ThreadPoolExecutor(2, 4, 30, TimeUnit.SECONDS,
            new ArrayBlockingQueue<>(64), Thread.ofPlatform().name("generation-ws-", 0).factory(),
            new ThreadPoolExecutor.AbortPolicy());

    private static final class Subscription {
        final WebSocketSession socket;
        final long connectedAt = System.nanoTime();
        final AtomicBoolean sending = new AtomicBoolean();
        volatile UUID jobId;
        volatile User user;
        volatile String token;
        volatile long lastVersion = -1;
        volatile Instant lastSent = Instant.EPOCH;
        Subscription(WebSocketSession socket) {
            this.socket = new ConcurrentWebSocketSessionDecorator(socket, 1000, 64 * 1024);
        }
    }

    @Override public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        if (sessions.size() >= 256 || session.getUri() == null || session.getUri().getRawQuery() != null) {
            session.close(CloseStatus.POLICY_VIOLATION); return;
        }
        session.setTextMessageSizeLimit(8192);
        sessions.put(session.getId(), new Subscription(session));
    }

    @Override protected void handleTextMessage(WebSocketSession session, TextMessage message) {
        var subscription = sessions.get(session.getId());
        if (subscription == null || subscription.user != null) { close(session.getId()); return; }
        try {
            var payload = json.readTree(message.getPayload());
            if (!payload.isObject() || payload.size() != 3 || !"SUBSCRIBE".equals(payload.path("type").asText())
                    || !payload.path("token").isString() || !payload.path("jobId").isString()) {
                close(session.getId()); return;
            }
            String token = payload.path("token").asText();
            if (token.isBlank() || token.length() > 7000) { close(session.getId()); return; }
            var details = users.loadUserByUsername(jwt.extractUsername(token));
            if (!(details instanceof User owner) || !jwt.isTokenValid(token, owner)) { close(session.getId()); return; }
            UUID jobId = UUID.fromString(payload.path("jobId").asText());
            store.get(jobId, owner); // Same ownership check as HTTP; existence of other users' jobs is not disclosed.
            subscription.token = token;
            subscription.jobId = jobId;
            subscription.user = owner;
        } catch (RuntimeException e) { close(session.getId()); }
    }

    @Scheduled(fixedDelayString = "${generation.websocket-poll-ms:2000}")
    public void dispatch() {
        sessions.forEach((id, subscription) -> {
            if (subscription.user == null) {
                if (System.nanoTime() - subscription.connectedAt > TimeUnit.SECONDS.toNanos(10)) close(id);
                return;
            }
            if (!subscription.sending.compareAndSet(false, true)) return;
            try { delivery.execute(() -> send(id, subscription)); }
            catch (RejectedExecutionException e) { subscription.sending.set(false); close(id); }
        });
    }

    private void send(String id, Subscription subscription) {
        try {
            if (!jwt.isTokenValid(subscription.token, subscription.user)) { close(id); return; }
            var snapshot = store.get(subscription.jobId, subscription.user);
            if (snapshot.getVersion() > subscription.lastVersion) {
                subscription.socket.sendMessage(new TextMessage(json.writeValueAsString(snapshot)));
                subscription.lastVersion = snapshot.getVersion();
                subscription.lastSent = Instant.now();
                if (snapshot.getStatus() == tech.genna.TY3.api.model.GenerationJob.StatusEnum.COMPLETED
                        || snapshot.getStatus() == tech.genna.TY3.api.model.GenerationJob.StatusEnum.FAILED) {
                    close(id, CloseStatus.NORMAL);
                }
            } else if (subscription.lastSent.plusSeconds(15).isBefore(Instant.now())) {
                subscription.socket.sendMessage(new PingMessage());
                subscription.lastSent = Instant.now();
            }
        } catch (Exception e) { close(id); }
        finally { subscription.sending.set(false); }
    }

    private void close(String id) { close(id, CloseStatus.POLICY_VIOLATION); }
    private void close(String id, CloseStatus status) {
        var subscription = sessions.remove(id);
        if (subscription != null) {
            subscription.token = null;
            try { subscription.socket.close(status); } catch (IOException ignored) { }
        }
    }
    @Override public void afterConnectionClosed(WebSocketSession session, CloseStatus status) { sessions.remove(session.getId()); }
    @Override public void handleTransportError(WebSocketSession session, Throwable error) { close(session.getId()); }
    @PreDestroy public void stop() {
        sessions.keySet().forEach(this::close);
        delivery.shutdownNow();
    }
}
