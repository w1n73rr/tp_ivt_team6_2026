package tech.genna.TY3.ml;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;
import static tech.genna.TY3.ml.GenerationEvent.Stage.*;

/** Blocking operation: BE must call on its background job executor, not in the submit HTTP request. */
public final class QuizGenerator {
    private final ModelClient client;
    private final QuizValidator validator = new QuizValidator();
    private final int maxInputChars;
    private final String prompt;
    private final QuizReviewer reviewer;
    private final Duration totalTimeout;

    public QuizGenerator(ModelClient client, int maxInputChars) {
        this(client, maxInputChars, Duration.ofSeconds(240));
    }

    public QuizGenerator(ModelClient client, int maxInputChars, Duration totalTimeout) {
        this.client = Objects.requireNonNull(client);
        if (totalTimeout == null || totalTimeout.isZero() || totalTimeout.isNegative()) {
            throw new IllegalArgumentException("Invalid total generation timeout");
        }
        this.totalTimeout = totalTimeout;
        reviewer = new QuizReviewer(client);
        this.maxInputChars = maxInputChars;
        try (var input = QuizGenerator.class.getResourceAsStream("/ml/prompt.txt")) {
            if (input == null) throw new IllegalStateException("Missing ml/prompt.txt");
            prompt = new String(input.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException ex) { throw new IllegalStateException("Cannot load ML prompt"); }
    }

    public Quiz generate(GenerationRequest request, Consumer<GenerationEvent> listener) {
        Objects.requireNonNull(request);
        Objects.requireNonNull(listener);
        AtomicLong sequence = new AtomicLong();
        AtomicBoolean terminal = new AtomicBoolean();
        Consumer<GenerationEvent.Stage> emit = stage -> {
            if (terminal.get()) return;
            if ((stage == FAILED || stage == VALIDATED) && !terminal.compareAndSet(false, true)) return;
            // A disconnected UI must not turn successful model work into a retry/billed duplicate.
            try { listener.accept(new GenerationEvent(request.requestId(), stage, sequence.incrementAndGet())); }
            catch (RuntimeException ignored) { /* BE owns delivery/reconnection and durable status. */ }
        };
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            request.validate(maxInputChars);
            Future<Quiz> work = executor.submit(() -> run(request, emit));
            try { return work.get(totalTimeout.toMillis(), TimeUnit.MILLISECONDS); }
            catch (TimeoutException ex) {
                work.cancel(true); throw new GenerationFailure(GenerationFailure.Code.MODEL_TIMEOUT);
            } catch (InterruptedException ex) {
                work.cancel(true); Thread.currentThread().interrupt();
                throw new GenerationFailure(GenerationFailure.Code.CANCELLED);
            } catch (ExecutionException ex) {
                if (ex.getCause() instanceof GenerationFailure failure) throw failure;
                throw new GenerationFailure(GenerationFailure.Code.INVALID_MODEL_OUTPUT);
            }
        } catch (GenerationFailure ex) { emit.accept(FAILED); throw ex; }
    }

    private Quiz run(GenerationRequest request, Consumer<GenerationEvent.Stage> emit) {
        Map<String, Object> input = new LinkedHashMap<>(Map.of("requestId", request.requestId(), "settings", Map.of(
                "questionCount", request.questionCount(), "difficulty", request.difficulty().name(),
                "questionType", request.questionType().name()), "material", Map.of(
                "id", request.materialId(), "text", request.text())));
        String system = prompt.replace("{{EXAMPLE}}", example(request)).replace("{{TYPE_RULES}}", selectedRules(request));
        emit.accept(PREPARING_PROMPT);
        for (int attempt = 0; attempt < 2; attempt++) {
            emit.accept(WAITING_FOR_MODEL);
            // Transport failures are never retried: provider may have already processed the request.
            String raw = client.complete(system, Json.write(input), () -> emit.accept(RECEIVING_RESPONSE));
            emit.accept(VALIDATING);
            Quiz quiz;
            try { quiz = validator.parse(raw, request); }
            catch (GenerationFailure failure) {
                if (attempt != 0 || failure.code() != GenerationFailure.Code.INVALID_MODEL_OUTPUT) throw failure;
                input.put("correction", Map.of("code", "INVALID_MODEL_OUTPUT", "issue", failure.validationHint(),
                        "instruction", "Return a complete new response. Check requestId, question count and the correct-answer count in EVERY question. " + selectedRules(request)));
                emit.accept(REVISING); continue;
            }
            emit.accept(REVIEWING_CONTENT);
            List<Map<String, String>> issues = reviewer.review(request, quiz);
            if (issues.isEmpty()) {
                Quiz result = shuffle(quiz);
                emit.accept(VALIDATED);
                return result;
            }
            if (attempt == 1) throw new GenerationFailure(GenerationFailure.Code.QUALITY_REJECTED);
            input.put("correction", Map.of("issues", issues, "previousQuiz", quiz));
            emit.accept(REVISING);
        }
        throw new GenerationFailure(GenerationFailure.Code.QUALITY_REJECTED);
    }

    private static String selectedRules(GenerationRequest request) {
        String rules = switch (request.questionType()) {
            case single_choice -> "Each question has exactly four options a,b,c,d and ONE correct answer. "
                    + "Do not split several correct members of a list across different options.";
            case multiple_choice -> "EVERY question has exactly four options a,b,c,d and TWO OR THREE correct answers. "
                    + "One correct answer or four correct answers is invalid. Ask the learner to select all valid statements. "
                    + "First choose several independent source-supported statements on one coherent topic, then add "
                    + "one or two distractors. Do not ask for a single term or inflate the key using duplicates. "
                    + "For medium or hard, ask which decisions or consequences follow from ONE scenario; "
                    + "do not replace reasoning with a checklist of unrelated true statements. "
                    + "Explain every correct statement.";
            case true_false -> "Each question contains one statement and exactly the options true/Верно and false/Неверно. "
                    + "Include both true and false answers, approximately balanced. The explanation corrects a false statement. "
                    + "For medium or hard, present a short scenario and ONE conclusion to evaluate. "
                    + "The conclusion must require the selected level of reasoning, not just recalling a number. "
                    + "Do not combine independent claims into one statement.";
        };
        return "\nRUN SETTINGS\nType of EVERY question: " + request.questionType().name()
                + ". Exact count: " + request.questionCount() + ". " + rules + "\n";
    }

    private static String example(GenerationRequest request) {
        boolean tf = request.questionType() == GenerationRequest.QuestionType.true_false;
        List<Quiz.Option> options = tf ? List.of(new Quiz.Option("true", "Верно"), new Quiz.Option("false", "Неверно"))
                : List.of(new Quiz.Option("a", "Вариант A"), new Quiz.Option("b", "Вариант B"),
                new Quiz.Option("c", "Вариант C"), new Quiz.Option("d", "Вариант D"));
        List<String> correct = tf ? List.of("false") : request.questionType() == GenerationRequest.QuestionType.multiple_choice
                ? List.of("a", "c") : List.of("b");
        return Json.write(Map.of("requestId", request.requestId(), "status", "success", "quiz", new Quiz(
                2, request.materialId(), "Название по материалу", List.of(new Quiz.Question("q1", request.questionType(),
                tf ? "Утверждение по материалу" : request.questionType() == GenerationRequest.QuestionType.multiple_choice
                        ? "Выберите все утверждения, которые соответствуют описанной в материале связи." : "Вопрос по материалу", options, correct, "Обоснование по материалу")))));
    }

    private static Quiz shuffle(Quiz quiz) {
        List<Quiz.Question> questions = quiz.questions().stream().map(q -> {
            if (q.type() == GenerationRequest.QuestionType.true_false) return q;
            List<Quiz.Option> options = new ArrayList<>(q.options());
            Collections.shuffle(options);
            List<Quiz.Option> reordered = new ArrayList<>();
            List<String> answers = new ArrayList<>();
            for (int i = 0; i < options.size(); i++) {
                String id = String.valueOf((char) ('a' + i));
                Quiz.Option old = options.get(i);
                reordered.add(new Quiz.Option(id, old.text()));
                if (q.correctOptionIds().contains(old.id())) answers.add(id);
            }
            return new Quiz.Question(q.id(), q.type(), q.text(), reordered, answers, q.explanation());
        }).toList();
        return new Quiz(quiz.schemaVersion(), quiz.materialId(), quiz.title(), questions);
    }
}
