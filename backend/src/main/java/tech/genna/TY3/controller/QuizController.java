package tech.genna.TY3.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;
import tech.genna.TY3.api.SavedQuizzesApi;
import tech.genna.TY3.api.model.*;
import tech.genna.TY3.security.CurrentUser;
import tech.genna.TY3.service.quiz.QuizService;

import java.util.UUID;

@RestController
@RequiredArgsConstructor
public class QuizController implements SavedQuizzesApi {
    private final QuizService quizzes;
    private final CurrentUser currentUser;

    @Override
    public ResponseEntity<QuizListResponse> listQuizzes() {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(quizzes.list(currentUser.get().getId()));
    }

    @Override
    public ResponseEntity<QuizDetail> getQuiz(UUID quizId) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(quizzes.get(quizId, currentUser.get().getId()));
    }

    @Override
    public ResponseEntity<QuizSummary> renameQuiz(UUID quizId, RenameQuizRequest request) {
        return ResponseEntity.ok(quizzes.rename(quizId, currentUser.get().getId(), request.getName()));
    }

    @Override
    public ResponseEntity<Void> deleteQuiz(UUID quizId) {
        quizzes.delete(quizId, currentUser.get().getId());
        return ResponseEntity.noContent().build();
    }

}
