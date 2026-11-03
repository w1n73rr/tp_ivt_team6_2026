package tech.genna.TY3.controller;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;
import tech.genna.TY3.api.*;
import tech.genna.TY3.api.model.AuthResponse;
import tech.genna.TY3.api.model.FileFormat;
import tech.genna.TY3.api.model.MaterialListResponse;
import tech.genna.TY3.api.model.MaterialSummary;
import tech.genna.TY3.api.model.SubjectResponse;
import tech.genna.TY3.api.model.TagListItemResponse;
import tech.genna.TY3.entity.User;
import tech.genna.TY3.repository.MaterialRepository;
import tech.genna.TY3.security.CurrentUser;
import tech.genna.TY3.service.auth.AuthService;
import tech.genna.TY3.service.file.FileStorageService;
import tech.genna.TY3.service.material.MaterialService;
import tech.genna.TY3.service.subject.SubjectService;
import tech.genna.TY3.service.tag.TagService;
import tech.genna.TY3.service.generation.GenerationParametersService;

import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class GeneratedApiContractTest {

    private MockMvc mockMvc;
    private SubjectService subjectService;
    private TagService tagService;
    private AuthService authService;
    private MaterialService materialService;
    private CurrentUser currentUser;
    private User user;

    @BeforeEach
    void setUp() {
        subjectService = mock(SubjectService.class);
        tagService = mock(TagService.class);
        authService = mock(AuthService.class);
        materialService = mock(MaterialService.class);
        currentUser = mock(CurrentUser.class);
        user = User.builder().id(UUID.randomUUID()).build();
        when(currentUser.get()).thenReturn(user);
        mockMvc = MockMvcBuilders.standaloneSetup(new SubjectController(subjectService, currentUser),
                        new TagController(tagService, currentUser), new AuthController(authService),
                        new MaterialController(materialService, currentUser),
                        new FileController(mock(FileStorageService.class), mock(MaterialRepository.class), currentUser),
                        new MvpController(), new GenerationParametersController(mock(GenerationParametersService.class)))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void emptyReferenceListsUseItemsEnvelope() throws Exception {
        when(subjectService.findAll(user)).thenReturn(List.of());
        when(tagService.findAll(user)).thenReturn(List.of());
        when(materialService.findAll(user)).thenReturn(new MaterialListResponse(List.of()));

        mockMvc.perform(get("/api/subjects")).andExpect(status().isOk())
                .andExpect(content().json("{\"items\":[]}"));
        mockMvc.perform(get("/api/tags")).andExpect(status().isOk())
                .andExpect(content().json("{\"items\":[]}"));
        mockMvc.perform(get("/api/materials")).andExpect(status().isOk())
                .andExpect(content().json("{\"items\":[]}"));
    }

    @Test
    void materialListReturnsContractFieldsForCurrentUser() throws Exception {
        UUID materialId = UUID.randomUUID();
        UUID subjectId = UUID.randomUUID();
        var summary = new MaterialSummary(materialId, "SQL lecture", FileFormat.PDF, 4096L,
                subjectId, "Databases", java.time.OffsetDateTime.parse("2026-10-20T10:00:00Z"));
        when(materialService.findAll(user)).thenReturn(new MaterialListResponse(List.of(summary)));

        mockMvc.perform(get("/api/materials")).andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].id").value(materialId.toString()))
                .andExpect(jsonPath("$.items[0].name").value("SQL lecture"))
                .andExpect(jsonPath("$.items[0].format").value("PDF"))
                .andExpect(jsonPath("$.items[0].sizeBytes").value(4096))
                .andExpect(jsonPath("$.items[0].subjectId").value(subjectId.toString()))
                .andExpect(jsonPath("$.items[0].subjectName").value("Databases"))
                .andExpect(jsonPath("$.items[0].uploadedAt").exists());
    }

    @Test
    void referenceListFieldsMatchGeneratedModels() throws Exception {
        UUID subjectId = UUID.randomUUID();
        UUID tagId = UUID.randomUUID();
        when(subjectService.findAll(user)).thenReturn(List.of(new SubjectResponse(subjectId, "Математика")));
        when(tagService.findAll(user)).thenReturn(List.of(new TagListItemResponse(tagId, "Алгебра")));

        mockMvc.perform(get("/api/subjects")).andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].id").value(subjectId.toString()))
                .andExpect(jsonPath("$.items[0].name").value("Математика"));
        mockMvc.perform(get("/api/tags")).andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].id").value(tagId.toString()))
                .andExpect(jsonPath("$.items[0].name").value("Алгебра"))
                .andExpect(jsonPath("$.items[0].subjectId").doesNotExist());
    }

    @Test
    void generatedConstraintsRejectBlankSubjectWithContractError() throws Exception {
        mockMvc.perform(post("/api/subjects").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"   \"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.fieldErrors[0].field").value("name"));
    }

    @Test
    void missingPrincipalReturnsContractAuthenticationError() throws Exception {
        when(currentUser.get()).thenThrow(new AuthenticationCredentialsNotFoundException("Требуется вход"));

        mockMvc.perform(get("/api/subjects")).andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTHENTICATION_REQUIRED"));
        mockMvc.perform(get("/api/materials")).andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTHENTICATION_REQUIRED"));
    }

    @Test
    void generatedAuthMappingStillAcceptsExistingRequestShape() throws Exception {
        when(authService.register(any())).thenReturn(new AuthResponse("jwt"));

        mockMvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"student\",\"email\":\"student@example.org\",\"password\":\"secret123\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").value("jwt"));
    }

    @Test
    void unimplementedGeneratedEndpointsAreRegisteredAndReturn501() throws Exception {
        mockMvc.perform(get("/api/progress")).andExpect(status().isNotImplemented());
        mockMvc.perform(get("/api/attempts")).andExpect(status().isNotImplemented());
        mockMvc.perform(get("/api/users/me/preferences")).andExpect(status().isNotImplemented());
    }

    @Test
    void everyGeneratedOperationHasARegisteredHttpMapping() {
        var context = mockMvc.getDispatcherServlet().getWebApplicationContext();
        var mappings = context.getBean(RequestMappingHandlerMapping.class).getHandlerMethods().keySet();
        for (Class<?> api : List.of(AuthApi.class, SubjectsApi.class, TagsApi.class, MaterialsApi.class, FilesApi.class,
                QuizzesApi.class, GenerationApi.class, AttemptsApi.class, TrainingApi.class,
                AnalyticsApi.class, UsersApi.class)) {
            for (var method : api.getDeclaredMethods()) {
                var expected = method.getAnnotation(RequestMapping.class);
                if (expected != null) {
                    assertTrue(mappings.stream().anyMatch(mapping ->
                                    mapping.getPatternValues().containsAll(List.of(expected.value()))
                                            && mapping.getMethodsCondition().getMethods()
                                            .containsAll(List.of(expected.method()))),
                            () -> "Нет HTTP-маршрута для " + api.getSimpleName() + "." + method.getName());
                }
            }
        }
    }

    @Test
    void malformedJsonAndUnsupportedContentTypeKeepTheirHttpStatus() throws Exception {
        mockMvc.perform(post("/api/subjects").contentType(MediaType.APPLICATION_JSON).content("{"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
        mockMvc.perform(post("/api/subjects").contentType(MediaType.TEXT_PLAIN).content("name=test"))
                .andExpect(status().isUnsupportedMediaType())
                .andExpect(jsonPath("$.code").value("UNSUPPORTED_MEDIA_TYPE"));
    }
}
