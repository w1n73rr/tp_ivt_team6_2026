package tech.genna.TY3.generationui;

import com.sun.net.httpserver.HttpServer;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.jdbc.core.JdbcTemplate;
import tech.genna.TY3.Ty3Application;
import tech.genna.TY3.ml.GenerationRequest;
import tech.genna.TY3.ml.Quiz;
import tech.genna.TY3.service.generation.*;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

/** Test classpath only: real HTTP/SQL/S3/worker, controlled ML and fault injection. */
public final class GenerationUiTestServer {
    public static void main(String[] args) {
        String database = System.getenv("SPRING_DATASOURCE_URL");
        if (database == null || !database.matches("jdbc:postgresql://(localhost|127\\.0\\.0\\.1):[0-9]+/generation_ui_controlled")) {
            throw new IllegalStateException("Test server requires the separate loopback generation_ui_controlled database");
        }
        SpringApplication.run(new Class<?>[]{Ty3Application.class, Controlled.class}, args);
    }
    public static final class State {
        volatile String mode = "SUCCESS";
        final AtomicInteger calls = new AtomicInteger();
    }
    @TestConfiguration(proxyBeanMethods = false)
    public static class Controlled {
        @Bean State state() { return new State(); }
        @Bean(destroyMethod = "stop") Control control(State state, JdbcTemplate jdbc) throws Exception {
            return new Control(state, jdbc);
        }
        @Bean @Primary MlGenerator controlledMl(State state) {
            return (id, text, p) -> {
                state.calls.incrementAndGet();
                long deadline = System.nanoTime() + java.time.Duration.ofSeconds(25).toNanos();
                while (state.mode.equals("WAIT") && System.nanoTime() < deadline) {
                    try { Thread.sleep(50); } catch (InterruptedException e) { Thread.currentThread().interrupt(); throw new GenerationFailure("CANCELLED"); }
                }
                if (state.mode.equals("ML_UNAVAILABLE")) throw new GenerationFailure("MODEL_UNAVAILABLE");
                if (state.mode.equals("INVALID")) throw new GenerationFailure("INVALID_MODEL_OUTPUT");
                var type = GenerationRequest.QuestionType.valueOf(p.questionType().getValue().toLowerCase(Locale.ROOT));
                List<Quiz.Question> source = new ArrayList<>();
                List<MlGenerator.Question> mapped = new ArrayList<>();
                String[] facts = {"Модульное тестирование проверяет компоненты в изоляции", "Интеграционное тестирование проверяет взаимодействие компонентов", "Мок позволяет контролировать зависимость", "Регрессионный тест проверяет ранее работавшее поведение", "Тест-кейс содержит ожидаемый результат", "Негативный тест проверяет недопустимые данные", "Системный тест проверяет систему целиком", "Приёмочный тест проверяет потребности заказчика", "Граничные значения лежат около границ диапазона", "Позитивный тест использует допустимые данные"};
                int count = state.mode.equals("EMPTY") ? 0 : p.questionCount();
                for (int i = 0; i < count; i++) {
                    boolean tf = type == GenerationRequest.QuestionType.true_false;
                    var options = tf ? List.of(new Quiz.Option("true", "Верно"), new Quiz.Option("false", "Неверно"))
                            : List.of(new Quiz.Option("a", facts[i]), new Quiz.Option("b", "Отчёт содержит шаги воспроизведения"), new Quiz.Option("c", "Мок доказывает реальную интеграцию"), new Quiz.Option("d", "Один тест доказывает отсутствие всех дефектов"));
                    var correct = tf ? List.of("true") : type == GenerationRequest.QuestionType.multiple_choice ? List.of("a", "b") : List.of("a");
                    String question = "Проверка " + (i + 1) + ": " + facts[i] + "?";
                    source.add(new Quiz.Question("q" + (i + 1), type, question, options, correct, facts[i]));
                    mapped.add(new MlGenerator.Question(p.questionType(), question,
                            options.stream().map(o -> new MlGenerator.Option(o.text(), correct.contains(o.id()))).toList(), facts[i]));
                }
                return new MlGenerator.Result(id, mapped, new Quiz(2, p.materialId().toString(), p.name(), source));
            };
        }
        @Bean static BeanPostProcessor faults(State state) {
            return new BeanPostProcessor() {
                @Override public Object postProcessBeforeInitialization(Object bean, String name) {
                    if (bean instanceof DocumentTextExtractor extractor) {
                        var spy = spy(extractor);
                        doAnswer(call -> { if (state.mode.equals("EXTRACT")) throw new GenerationFailure("DOCUMENT_UNREADABLE"); return call.callRealMethod(); })
                                .when(spy).extract(any(), anyString());
                        return spy;
                    }
                    if (bean instanceof GenerationStore store) {
                        var spy = spy(store);
                        doAnswer(call -> { if (state.mode.equals("SAVE")) throw new IllegalStateException("Injected save failure"); return call.callRealMethod(); })
                                .when(spy).saveQuiz(any(), any());
                        return spy;
                    }
                    return bean;
                }
            };
        }
    }
    public static final class Control {
        private final HttpServer server;
        Control(State state, JdbcTemplate jdbc) throws Exception {
            server = HttpServer.create(new InetSocketAddress("127.0.0.1", 18084), 0);
            server.createContext("/result", exchange -> {
                String id = UUID.fromString(exchange.getRequestURI().getQuery()).toString();
                String result = jdbc.queryForObject("SELECT result_json FROM quizzes WHERE job_id = ?::uuid", String.class, id);
                byte[] bytes = result.getBytes(StandardCharsets.UTF_8);
                exchange.getResponseHeaders().set("Content-Type", "application/json");
                exchange.sendResponseHeaders(200, bytes.length);
                try (var output = exchange.getResponseBody()) { output.write(bytes); }
            });
            server.createContext("/control", exchange -> {
                var query = exchange.getRequestURI().getQuery();
                if (query != null && query.startsWith("mode=")) {
                    var mode = query.substring(5);
                    if (!Set.of("SUCCESS", "WAIT", "EXTRACT", "ML_UNAVAILABLE", "INVALID", "EMPTY", "SAVE").contains(mode)) { exchange.sendResponseHeaders(400, -1); exchange.close(); return; }
                    state.mode = mode;
                }
                String json = "{\"calls\":" + state.calls.get() + ",\"jobs\":" + jdbc.queryForObject("SELECT count(*) FROM generation_jobs", Long.class)
                        + ",\"quizzes\":" + jdbc.queryForObject("SELECT count(*) FROM quizzes", Long.class) + "}";
                byte[] bytes = json.getBytes(StandardCharsets.UTF_8);
                exchange.getResponseHeaders().set("Content-Type", "application/json");
                exchange.sendResponseHeaders(200, bytes.length);
                try (var output = exchange.getResponseBody()) { output.write(bytes); }
            });
            server.start();
        }
        public void stop() { server.stop(0); }
    }
}
