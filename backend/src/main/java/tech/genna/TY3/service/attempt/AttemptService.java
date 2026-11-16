package tech.genna.TY3.service.attempt;

import org.springframework.stereotype.Service;
import org.springframework.dao.DataAccessException;
import org.springframework.transaction.*;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import tech.genna.TY3.api.model.*;
import tech.genna.TY3.repository.*;
import tech.genna.TY3.repository.AttemptRepository.Attempt;
import tools.jackson.databind.*;
import tools.jackson.databind.node.ObjectNode;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.*;
import java.util.function.Supplier;

@Service
public class AttemptService {
    private final AttemptRepository attempts;
    private final QuizRepository quizzes;
    private final ObjectMapper json;
    private final TransactionTemplate writes;
    public AttemptService(AttemptRepository attempts, QuizRepository quizzes, ObjectMapper json, PlatformTransactionManager manager) {
        this.attempts=attempts; this.quizzes=quizzes; this.json=json;
        writes=new TransactionTemplate(manager);
        writes.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }
    public AttemptDetail start(UUID quizId, UUID owner, String key) {
        if (key == null || key.isBlank() || key.length()>128) throw error(400,"INVALID_IDEMPOTENCY_KEY");
        return database(() -> writes.execute(tx -> {
            quizzes.lockOwner(owner);
            var replay=attempts.replay(owner,key);
            if(replay.isPresent()) {
                var a=owned(replay.get(),owner,false);
                if(!a.quizId().equals(quizId)) throw error(409,"IDEMPOTENCY_KEY_REUSED");
                return detail(a);
            }
            var quiz=quizzes.findOwned(quizId,owner).orElseThrow(() -> error(404,"QUIZ_NOT_FOUND"));
            var a=new Attempt(UUID.randomUUID(),owner,quizId,json.valueToTree(quiz.summary()),
                    quiz.result().get("questions"),json.createObjectNode(),"IN_PROGRESS",OffsetDateTime.now(ZoneOffset.UTC).truncatedTo(java.time.temporal.ChronoUnit.MICROS));
            attempts.create(a,key);
            return detail(a);
        }));
    }
    public AttemptDetail get(UUID id, UUID owner) { return database(() -> detail(owned(id,owner,false))); }
    public AttemptResult result(UUID id, UUID owner) {
        return database(() -> {
            var a=owned(id,owner,false);
            if(!a.status().equals("COMPLETED")) throw error(409,"RESULT_NOT_READY");
            // Totals/time are persisted, never recalculated on read. Keys/selections are immutable after completion.
            var saved=attempts.completion(id,owner);
            var result=new AttemptResult().attemptId(saved.getAttemptId()).quiz(saved.getQuiz())
                    .score(saved.getScore()).scorePercent(saved.getScorePercent()).totalQuestions(saved.getTotalQuestions())
                    .correctCount(saved.getCorrectCount()).incorrectCount(saved.getIncorrectCount())
                    .skippedCount(saved.getSkippedCount()).durationSeconds(saved.getDurationSeconds()).completedAt(saved.getCompletedAt());
            var items=new ArrayList<QuestionResult>();
            for(var q:a.questions()) {
                var selected=new LinkedHashSet<UUID>();
                for(var option:a.answers().path(q.path("id").asText()).path("selectedOptionIds"))
                    selected.add(UUID.fromString(option.asText()));
                var correct=new LinkedHashSet<UUID>();
                for(var option:q.path("correctOptionIds")) correct.add(UUID.fromString(option.asText()));
                var outcome=selected.isEmpty()?QuestionResult.OutcomeEnum.SKIPPED:
                        selected.equals(correct)?QuestionResult.OutcomeEnum.CORRECT:QuestionResult.OutcomeEnum.INCORRECT;
                items.add(new QuestionResult().question(publicQuestion(q,items.size()+1))
                        .selectedOptionIds(selected).correctOptionIds(correct).outcome(outcome)
                        .score(outcome==QuestionResult.OutcomeEnum.CORRECT?1:0));
            }
            return result.items(items);
        });
    }
    public AttemptCompletion complete(UUID id, UUID owner) {
        return database(() -> writes.execute(tx -> {
            var a=owned(id,owner,true);
            if(a.status().equals("COMPLETED")) return attempts.completion(id,owner);
            if(!a.status().equals("IN_PROGRESS")) throw error(409,"ATTEMPT_CLOSED");
            var score=AttemptScorer.score(a.questions(),a.answers());
            var now=OffsetDateTime.now(ZoneOffset.UTC).truncatedTo(java.time.temporal.ChronoUnit.MICROS);
            var result=new AttemptCompletion().attemptId(id).quiz(json.treeToValue(a.quiz(),QuizSummary.class))
                    .totalQuestions(score.total()).correctCount(score.correct()).incorrectCount(score.incorrect())
                    .skippedCount(score.skipped()).score(score.correct()).scorePercent(score.percent())
                    .durationSeconds(Math.max(0,java.time.Duration.between(a.startedAt(),now).getSeconds())).completedAt(now);
            attempts.complete(id,owner,result);
            return result;
        }));
    }
    public SavedAnswer save(UUID id, UUID owner, UUID questionId, SaveAnswerRequest request) {
        return database(() -> writes.execute(tx -> {
            var a=owned(id,owner,true);
            if(!a.status().equals("IN_PROGRESS")) throw error(409,"ATTEMPT_CLOSED");
            JsonNode question=null;
            for(var q:a.questions()) if(q.path("id").asText().equals(questionId.toString())) question=q;
            if(question==null) throw error(404,"QUESTION_NOT_FOUND");
            var selected=request==null?null:request.getSelectedOptionIds();
            if(selected==null || selected.size()>4 || selected.stream().anyMatch(Objects::isNull) || new HashSet<>(selected).size()!=selected.size())
                throw error(400,"INVALID_ANSWER");
            var allowed=new ArrayList<UUID>();
            for(var o:question.path("options")) allowed.add(UUID.fromString(o.path("id").asText()));
            if(!allowed.containsAll(selected) || (!question.path("type").asText().equalsIgnoreCase("multiple_choice") && selected.size()>1))
                throw error(400,"INVALID_ANSWER");
            // Canonical question order makes repeated selections independent of request order.
            var canonical=new LinkedHashSet<>(allowed.stream().filter(selected::contains).toList());
            var previous=a.answers().get(questionId.toString());
            if(previous!=null) {
                var saved=json.treeToValue(previous,SavedAnswer.class);
                if(saved.getSelectedOptionIds().equals(canonical)) return saved;
            }
            var saved=new SavedAnswer(questionId,canonical,OffsetDateTime.now(ZoneOffset.UTC).truncatedTo(java.time.temporal.ChronoUnit.MICROS));
            ObjectNode updated=(ObjectNode)a.answers().deepCopy();
            updated.set(questionId.toString(),json.valueToTree(saved));
            attempts.answers(id,owner,updated);
            return saved;
        }));
    }
    private Attempt owned(UUID id,UUID owner,boolean lock) {
        return attempts.find(id,owner,lock).orElseThrow(() -> error(404,"ATTEMPT_NOT_FOUND"));
    }
    private AttemptDetail detail(Attempt a) {
        var questions=new ArrayList<Question>();
        var answers=new ArrayList<SavedAnswer>();
        int first=0, answered=0;
        for(var q:a.questions()) {
            questions.add(publicQuestion(q,questions.size()+1));
            var answer=a.answers().get(q.path("id").asText());
            if(answer!=null) answers.add(json.treeToValue(answer,SavedAnswer.class));
            if(answer!=null && !answer.path("selectedOptionIds").isEmpty()) answered++;
            else if(first==0) first=questions.size();
        }
        var summary=new AttemptSummary(a.id(),json.treeToValue(a.quiz(),QuizSummary.class),
                AttemptStatus.fromValue(a.status()),a.startedAt(),answered);
        if(a.status().equals("COMPLETED")) {
            var result=attempts.completion(a.id(),a.owner());
            summary.completedAt(result.getCompletedAt()).scorePercent(result.getScorePercent());
        }
        return new AttemptDetail(summary,questions,answers,first==0?questions.size():first);
    }
    static Question publicQuestion(JsonNode q,int position) {
        var options=new ArrayList<QuestionOption>();
        for(var o:q.path("options")) options.add(new QuestionOption(UUID.fromString(o.path("id").asText()),o.path("text").asText()));
        return new Question(UUID.fromString(q.path("id").asText()),
                QuestionType.fromValue(q.path("type").asText().toUpperCase(Locale.ROOT)),q.path("text").asText(),position,options);
    }
    static ResponseStatusException error(int status,String code) { return new ResponseStatusException(HttpStatus.valueOf(status),code); }
    private static <T>T database(Supplier<T> operation) {
        try { return operation.get(); }
        catch(DataAccessException | TransactionException | tools.jackson.core.JacksonException ex) {
            throw error(503,"ATTEMPT_STORAGE_UNAVAILABLE");
        }
    }
}
