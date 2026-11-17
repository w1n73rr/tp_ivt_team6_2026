package tech.genna.TY3.controller;

import org.junit.jupiter.api.*;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import tech.genna.TY3.api.model.*;
import tech.genna.TY3.entity.User;
import tech.genna.TY3.security.CurrentUser;
import tech.genna.TY3.service.attempt.AttemptService;
import java.util.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class AttemptStartControllerTest {
    final UUID owner=UUID.randomUUID(),quiz=UUID.randomUUID(),key=UUID.randomUUID(),id=UUID.randomUUID();
    AttemptService service;CurrentUser user;MockMvc mvc;
    @BeforeEach void setup() {
        service=mock(AttemptService.class);user=mock(CurrentUser.class);
        when(user.get()).thenReturn(User.builder().id(owner).build());
        mvc=MockMvcBuilders.standaloneSetup(new AttemptController(service,user))
                .setControllerAdvice(new GlobalExceptionHandler()).build();
    }
    @Test void createdAndResumedResponsesUseContractStatusAndAuthenticatedOwner() throws Exception {
        var detail=new AttemptDetail().attempt(new AttemptSummary().id(id).status(AttemptStatus.IN_PROGRESS))
                .questions(List.of()).answers(List.of()).currentPosition(1);
        when(service.start(quiz,owner,key.toString())).thenReturn(new AttemptService.StartOutcome(detail,true),new AttemptService.StartOutcome(detail,false));
        mvc.perform(post("/api/quizzes/{id}/attempts",quiz).header("Idempotency-Key",key))
                .andExpect(status().isCreated()).andExpect(header().string("Cache-Control","no-store"))
                .andExpect(jsonPath("$.attempt.id").value(id.toString()));
        mvc.perform(post("/api/quizzes/{id}/attempts",quiz).header("Idempotency-Key",key))
                .andExpect(status().isOk()).andExpect(header().string("Cache-Control","no-store"))
                .andExpect(jsonPath("$.attempt.id").value(id.toString()));
        verify(service,times(2)).start(quiz,owner,key.toString());
    }
    @Test void missingOrInvalidRequestKeyDoesNotCallService() throws Exception {
        mvc.perform(post("/api/quizzes/{id}/attempts",quiz)).andExpect(status().isBadRequest());
        mvc.perform(post("/api/quizzes/{id}/attempts",quiz).header("Idempotency-Key","bad"))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(service);
    }
    @Test void missingPrincipalCannotStartOrContinue() throws Exception {
        when(user.get()).thenThrow(new AuthenticationCredentialsNotFoundException("Required"));
        mvc.perform(post("/api/quizzes/{id}/attempts",quiz).header("Idempotency-Key",key)).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/attempts/{id}",id)).andExpect(status().isUnauthorized());
        verifyNoInteractions(service);
    }
}
