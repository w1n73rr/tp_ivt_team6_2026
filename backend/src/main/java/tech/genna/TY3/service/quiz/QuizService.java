package tech.genna.TY3.service.quiz;

import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionException;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;
import tech.genna.TY3.api.model.*;
import tech.genna.TY3.repository.QuizRepository;
import tech.genna.TY3.repository.QuizRepository.StoredQuiz;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;

import java.util.ArrayList;
import java.util.Locale;
import java.util.UUID;
import java.util.function.Supplier;

@Service
public class QuizService {
    private final QuizRepository quizzes;
    private final TransactionTemplate writes;
    public QuizService(QuizRepository quizzes, PlatformTransactionManager manager) {
        this.quizzes = quizzes;
        writes = new TransactionTemplate(manager);
        writes.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    public QuizListResponse list(UUID ownerId) {
        return database(() -> new QuizListResponse(quizzes.listOwned(ownerId)));
    }

    public QuizDetail get(UUID id, UUID ownerId) {
        return database(() -> {
            var saved = owned(id, ownerId);
            var parameters = saved.parameters();
            var questions = new ArrayList<Question>();
            for (JsonNode q : saved.result().get("questions")) {
                var options = new ArrayList<QuestionOption>();
                for (JsonNode option : q.get("options")) {
                    options.add(new QuestionOption().id(UUID.fromString(option.get("id").asText()))
                            .text(option.get("text").asText()));
                }
                // Explicit public projection: answer key, explanations and ML-local IDs are never copied.
                questions.add(new Question().id(UUID.fromString(q.get("id").asText())).position(questions.size() + 1)
                        .type(QuestionType.fromValue(q.get("type").asText().toUpperCase(Locale.ROOT)))
                        .text(q.get("text").asText()).options(options));
            }
            return new QuizDetail().quiz(saved.summary()).settings(new GenerationSettings(
                    QuestionCount.fromValue(parameters.questionCount()), parameters.difficulty(), parameters.questionType()))
                    .questions(questions);
        });
    }

    public QuizSummary rename(UUID id, UUID ownerId, String name) {
        return database(() -> writes.execute(status -> {
            quizzes.lockOwner(ownerId);
            owned(id, ownerId);
            String normalized = QuizNames.normalize(name);
            if (quizzes.nameExists(ownerId, normalized, id))
                throw new ResponseStatusException(HttpStatus.CONFLICT, "QUIZ_NAME_ALREADY_EXISTS");
            if (quizzes.renameOwned(id, ownerId, normalized) != 1) throw notFound();
            return owned(id, ownerId).summary();
        }));
    }

    public void delete(UUID id, UUID ownerId) {
        database(() -> writes.execute(status -> {
            quizzes.lockOwner(ownerId);
            if (quizzes.deleteOwned(id, ownerId) != 1) throw notFound();
            return null;
        }));
    }

    private StoredQuiz owned(UUID id, UUID ownerId) {
        return quizzes.findOwned(id, ownerId).orElseThrow(QuizService::notFound);
    }
    private static <T> T database(Supplier<T> action) {
        try { return action.get(); }
        catch (DataAccessException | TransactionException | JacksonException ex) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "QUIZ_STORAGE_UNAVAILABLE", ex);
        }
    }
    private static ResponseStatusException notFound() {
        return new ResponseStatusException(HttpStatus.NOT_FOUND, "QUIZ_NOT_FOUND");
    }
}
