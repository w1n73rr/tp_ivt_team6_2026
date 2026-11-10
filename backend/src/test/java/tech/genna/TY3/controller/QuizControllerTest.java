package tech.genna.TY3.controller;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.server.ResponseStatusException;
import tech.genna.TY3.api.model.*;
import tech.genna.TY3.entity.User;
import tech.genna.TY3.security.CurrentUser;
import tech.genna.TY3.service.generation.GenerationParametersService;
import tech.genna.TY3.service.quiz.QuizService;

import java.util.List;
import java.util.UUID;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class QuizControllerTest {
    private QuizService quizzes;
    private CurrentUser current;
    private MockMvc mvc;
    private final UUID owner = UUID.randomUUID(), id = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        quizzes = mock(QuizService.class);
        current = mock(CurrentUser.class);
        when(current.get()).thenReturn(User.builder().id(owner).build());
        mvc = MockMvcBuilders.standaloneSetup(new QuizController(quizzes, current))
                .setControllerAdvice(new GlobalExceptionHandler()).build();
    }

    @Test
    void listUsesItemsAndCurrentUser() throws Exception {
        when(quizzes.list(owner)).thenReturn(new QuizListResponse(List.of()));
        mvc.perform(get("/api/quizzes")).andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-store")).andExpect(content().json("{\"items\":[]}"));
        verify(quizzes).list(owner);
    }

    @Test
    void getAndRenameUseGeneratedMappings() throws Exception {
        when(quizzes.get(id, owner)).thenReturn(new QuizDetail().quiz(new QuizSummary().id(id).name("Name"))
                .settings(new GenerationSettings(QuestionCount.NUMBER_5, Difficulty.MEDIUM, QuestionType.SINGLE_CHOICE)).questions(List.of()));
        mvc.perform(get("/api/quizzes/{quizId}", id)).andExpect(status().isOk())
                .andExpect(jsonPath("$.quiz.id").value(id.toString()))
                .andExpect(jsonPath("$.settings.questionType").value("SINGLE_CHOICE"));
        when(quizzes.rename(id, owner, "New")).thenReturn(new QuizSummary().id(id).name("New"));
        mvc.perform(patch("/api/quizzes/{quizId}", id).contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"New\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.name").value("New"));
        verify(quizzes).rename(id, owner, "New");
    }

    @Test
    void deleteIs204OnlyOnSuccessfulServiceReturn() throws Exception {
        mvc.perform(delete("/api/quizzes/{quizId}", id)).andExpect(status().isNoContent()).andExpect(content().string(""));
        doThrow(new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "QUIZ_STORAGE_UNAVAILABLE"))
                .when(quizzes).delete(id, owner);
        mvc.perform(delete("/api/quizzes/{quizId}", id)).andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("QUIZ_STORAGE_UNAVAILABLE"));
    }

    @Test
    void missingNameAndMalformedIdAre400() throws Exception {
        mvc.perform(patch("/api/quizzes/{quizId}", id).contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/api/quizzes/not-a-uuid")).andExpect(status().isBadRequest());
        verifyNoInteractions(quizzes);
    }

    @Test
    void missingAuthenticationIs401() throws Exception {
        when(current.get()).thenThrow(new org.springframework.security.authentication.AuthenticationCredentialsNotFoundException("Missing"));
        mvc.perform(get("/api/quizzes")).andExpect(status().isUnauthorized());
        verifyNoInteractions(quizzes);
    }
}
