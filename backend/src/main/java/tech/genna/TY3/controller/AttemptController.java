package tech.genna.TY3.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.RestController;
import tech.genna.TY3.api.AttemptsApi;
import tech.genna.TY3.api.model.*;
import tech.genna.TY3.security.CurrentUser;
import tech.genna.TY3.service.attempt.AttemptService;
import java.util.UUID;

@RestController
@RequiredArgsConstructor
public class AttemptController implements AttemptsApi {
    private final AttemptService attempts;
    private final CurrentUser user;
    public ResponseEntity<AttemptDetail> startAttempt(UUID quizId,UUID idempotencyKey) {
        var outcome=attempts.start(quizId,user.get().getId(),idempotencyKey.toString());
        return ResponseEntity.status(outcome.created()?201:200).cacheControl(CacheControl.noStore()).body(outcome.detail());
    }
    public ResponseEntity<AttemptDetail> getAttempt(UUID attemptId) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(attempts.get(attemptId,user.get().getId()));
    }
    public ResponseEntity<SavedAnswer> saveAnswer(UUID attemptId,UUID questionId,SaveAnswerRequest request) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(attempts.save(attemptId,user.get().getId(),questionId,request));
    }
    public ResponseEntity<AttemptCompletion> completeAttempt(UUID attemptId) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(attempts.complete(attemptId,user.get().getId()));
    }
    public ResponseEntity<AttemptResult> getAttemptResult(UUID attemptId) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(attempts.result(attemptId,user.get().getId()));
    }
}
