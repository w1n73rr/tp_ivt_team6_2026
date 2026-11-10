package tech.genna.TY3.config;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.*;
import tech.genna.TY3.service.generation.GenerationWebSocket;

@Configuration
@EnableWebSocket
@RequiredArgsConstructor
public class GenerationWebSocketConfig implements WebSocketConfigurer {
    private final GenerationWebSocket handler;
    @Value("${APP_CORS_ALLOWED_ORIGINS:http://localhost:3000}")
    private String origins;
    @Override public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(handler, "/api/ws/generation").setAllowedOrigins(origins.split(","));
    }
}
