package tech.genna.TY3.controller;

import org.junit.jupiter.api.*;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.test.web.servlet.*;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;
import tech.genna.TY3.api.model.*;
import tech.genna.TY3.entity.User;
import tech.genna.TY3.security.CurrentUser;
import tech.genna.TY3.service.generation.*;
import java.time.OffsetDateTime;
import java.util.UUID;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class GenerationControllerTest {
    GenerationParametersService parameters = mock(GenerationParametersService.class);
    GenerationStore store = mock(GenerationStore.class);
    CurrentUser principal = mock(CurrentUser.class);
    User owner = User.builder().id(UUID.randomUUID()).build();
    UUID id = UUID.randomUUID();
    MockMvc mvc;
    String valid = "{\"materialId\":\"" + UUID.randomUUID() + "\",\"questionCount\":5,\"difficulty\":\"MEDIUM\",\"questionType\":\"SINGLE_CHOICE\"}";
    @BeforeEach void setup() {
        when(principal.get()).thenReturn(owner);
        var options = mock(tech.genna.TY3.repository.GenerationOptionsRepository.class);
        when(options.findAll()).thenReturn(GenerationParametersServiceTest.initialOptions());
        var snapshots = mock(tech.genna.TY3.repository.GenerationSettingsSnapshotRepository.class);
        var catalog = new GenerationParametersService(options, snapshots).getParameters();
        when(parameters.getParameters()).thenReturn(catalog);
        mvc = MockMvcBuilders.standaloneSetup(new GenerationController(store, principal, parameters))
                .setControllerAdvice(new GlobalExceptionHandler()).build();
    }
    @Test void acceptedResponseHasJobIdAndPollingLocation() throws Exception {
        var job = new GenerationJob(id, UUID.randomUUID(), QuizKind.TEST, GenerationJob.StatusEnum.QUEUED, 0, OffsetDateTime.now(), OffsetDateTime.now());
        when(store.enqueue(any(), any(), eq(owner))).thenReturn(job);
        mvc.perform(post("/api/quizzes").header("Idempotency-Key", UUID.randomUUID()).contentType(MediaType.APPLICATION_JSON).content(valid))
                .andExpect(status().isAccepted()).andExpect(header().string("Location", "/api/generation-jobs/" + id))
                .andExpect(jsonPath("$.id").value(id.toString()));
    }
    @Test void invalidParametersAndMissingKeyDoNotEnqueue() throws Exception {
        for (String body : new String[]{valid.replace(":5", ":20"), valid.replace("SINGLE_CHOICE", "UNKNOWN"),
                valid.replace("\"SINGLE_CHOICE\"", "[\"SINGLE_CHOICE\",\"TRUE_FALSE\"]"),
                valid.replace("MEDIUM", "medium"), valid.replace(":5", ":null"), "{}"}) {
            mvc.perform(post("/api/quizzes").header("Idempotency-Key", UUID.randomUUID()).contentType(MediaType.APPLICATION_JSON).content(body))
                    .andExpect(status().isBadRequest());
        }
        mvc.perform(post("/api/quizzes").contentType(MediaType.APPLICATION_JSON).content(valid)).andExpect(status().isBadRequest());
        verifyNoInteractions(store);
    }
    @Test void authenticationFailureDoesNotEnqueue() throws Exception {
        when(principal.get()).thenThrow(new AuthenticationCredentialsNotFoundException("login required"));
        mvc.perform(post("/api/quizzes").header("Idempotency-Key", UUID.randomUUID()).contentType(MediaType.APPLICATION_JSON).content(valid))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(store);
    }
    @Test void statusIsOwnerScopedAndFailureRemainsHttp200() throws Exception {
        var job = new GenerationJob(id, UUID.randomUUID(), QuizKind.TEST, GenerationJob.StatusEnum.FAILED, 0, OffsetDateTime.now(), OffsetDateTime.now())
                .error(new TaskError("DOCUMENT_NO_TEXT", "Нет текста", false));
        when(store.get(id, owner)).thenReturn(job);
        mvc.perform(get("/api/generation-jobs/" + id)).andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("FAILED")).andExpect(jsonPath("$.error.code").value("DOCUMENT_NO_TEXT"));
        when(store.get(id, owner)).thenThrow(new ResponseStatusException(HttpStatus.NOT_FOUND, "NOT_FOUND"));
        mvc.perform(get("/api/generation-jobs/" + id)).andExpect(status().isNotFound());
    }
    @Test void catalogueUsesSupportedServerValues() throws Exception {
        mvc.perform(get("/api/quizzes/generation-options")).andExpect(status().isOk())
                .andExpect(jsonPath("$.counts[0]").value(5)).andExpect(jsonPath("$.counts[1]").value(10))
                .andExpect(jsonPath("$.types.length()").value(3));
    }
}
