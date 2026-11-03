package tech.genna.TY3.ml;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import java.nio.file.*;
import java.time.Duration;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/** Opt-in real request. Never runs during ordinary tests and never prints the credential. */
@EnabledIfEnvironmentVariable(named = "ML_LIVE_TEST", matches = "true")
class OpenRouterLiveTest {
    @ParameterizedTest @EnumSource(GenerationRequest.QuestionType.class)
    void generatesRealQuizFromPreparedLecture(GenerationRequest.QuestionType type) throws Exception {
        String selection = System.getenv().getOrDefault("ML_LIVE_TYPE", "single_choice");
        assumeTrue(selection.equals("all") || selection.equals(type.name())
                || selection.equals("remaining") && type != GenerationRequest.QuestionType.single_choice);
        String key = System.getenv("OPENROUTER_API_KEY");
        assertNotNull(key, "Set OPENROUTER_API_KEY locally; do not commit it");
        var difficulty = GenerationRequest.Difficulty.valueOf(
                System.getenv().getOrDefault("ML_LIVE_DIFFICULTY", "medium"));
        int count = Integer.parseInt(System.getenv().getOrDefault("ML_LIVE_COUNT", "5"));
        String source;
        try (var input = getClass().getResourceAsStream("/ml/demo-lecture.txt")) {
            assertNotNull(input);
            source = new String(input.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
        }
        String model = System.getenv().getOrDefault("OPENROUTER_MODEL", "nvidia/nemotron-3.5-lightning:free");
        boolean reasoning = Boolean.parseBoolean(System.getenv().getOrDefault("OPENROUTER_REASONING_ENABLED", "false"));
        var client = new OpenRouterClient(key, model, Duration.ofSeconds(90), 12000, reasoning);
        Path folder = Path.of("build", "ml-live");
        Files.createDirectories(folder);
        String prefix = type.name() + "-" + count + "-" + difficulty;
        for (String suffix : List.of(".json", ".run.json", ".raw.json")) {
            Files.deleteIfExists(folder.resolve(prefix + suffix));
        }
        try (var previousCalls = Files.list(folder)) {
            for (Path file : previousCalls.filter(p -> p.getFileName().toString().startsWith(prefix + ".call-"))
                    .filter(p -> p.getFileName().toString().endsWith(".json")).toList()) {
                Files.delete(file);
            }
        }
        List<GenerationEvent> events = new java.util.concurrent.CopyOnWriteArrayList<>();
        var calls = new java.util.concurrent.atomic.AtomicInteger();
        ModelClient recording = (system, user, first) -> {
            String raw = client.complete(system, user, first);
            boolean review = system.startsWith("РОЛЬ ПРОВЕРЯЮЩЕГО");
            int number = calls.incrementAndGet();
            try {
                Files.writeString(folder.resolve(prefix + ".call-" + number + (review ? ".review.json" : ".generation.json")), raw);
                if (!review) Files.writeString(folder.resolve(prefix + ".raw.json"), raw);
            }
            catch (java.io.IOException ex) { throw new IllegalStateException("Cannot save local diagnostic output"); }
            return raw;
        };
        var generator = new QuizGenerator(recording, 60000);
        long started = System.nanoTime();
        try {
            Quiz quiz = generator.generate(new GenerationRequest("live-demo-" + type, "demo-lecture", source,
                    count, difficulty, type), events::add);
            assertEquals(count, quiz.questions().size());
            Files.writeString(folder.resolve(prefix + ".json"), Json.write(quiz));
            Files.writeString(folder.resolve(prefix + ".run.json"), Json.write(Map.of(
                    "status", "validated", "seconds", (System.nanoTime() - started) / 1e9,
                    "events", events, "completedCalls", calls.get(), "model", model, "reasoning", reasoning)));
        } catch (GenerationFailure failure) {
            Files.writeString(folder.resolve(prefix + ".run.json"), Json.write(Map.of(
                    "status", "failed", "code", failure.code(),
                    "seconds", (System.nanoTime() - started) / 1e9, "events", events, "completedCalls", calls.get(), "model", model, "reasoning", reasoning)));
            throw failure;
        }
        // Structural and model-assisted checks only. A person still reviews source grounding and ambiguity.
    }
}
