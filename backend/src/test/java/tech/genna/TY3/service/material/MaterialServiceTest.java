package tech.genna.TY3.service.material;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;
import tech.genna.TY3.api.model.FileFormat;
import tech.genna.TY3.api.model.MaterialSummary;
import tech.genna.TY3.entity.Material;
import tech.genna.TY3.entity.Subject;
import tech.genna.TY3.entity.User;
import tech.genna.TY3.repository.MaterialRepository;
import tech.genna.TY3.repository.SubjectRepository;
import tech.genna.TY3.repository.TagRepository;
import tech.genna.TY3.service.file.FileStorageService;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MaterialServiceTest {

    @Mock
    private MaterialRepository materialRepository;
    @Mock
    private SubjectRepository subjectRepository;
    @Mock
    private TagRepository tagRepository;
    @Mock
    private JdbcTemplate jdbcTemplate;
    @Mock
    private TransactionTemplate transactionTemplate;
    @Mock
    private MaterialFileValidator materialFileValidator;
    @Mock
    private FileStorageService fileStorageService;
    @Mock
    private MaterialFileCleanup materialFileCleanup;
    @InjectMocks
    private MaterialService materialService;

    @Test
    void findAllReturnsEmptyItemsWhenUserHasNoMaterials() {
        UUID userId = UUID.randomUUID();
        User user = User.builder().id(userId).build();
        when(materialRepository.findAllByUserIdOrderByCreatedAtDescIdDesc(userId)).thenReturn(List.of());

        assertEquals(List.of(), materialService.findAll(user).getItems());
        verify(materialRepository).findAllByUserIdOrderByCreatedAtDescIdDesc(userId);
    }

    @Test
    void findAllMapsOnlyMaterialsReturnedForRequestedUserInRepositoryOrder() {
        UUID firstUserId = UUID.randomUUID();
        UUID secondUserId = UUID.randomUUID();
        User firstUser = User.builder().id(firstUserId).build();
        User secondUser = User.builder().id(secondUserId).build();
        Material newest = material(firstUser, "Newest", OffsetDateTime.parse("2026-10-20T11:00:00Z"));
        Material older = material(firstUser, "Older", OffsetDateTime.parse("2026-10-20T10:00:00Z"));
        Material otherUserMaterial = material(secondUser, "Private", OffsetDateTime.parse("2026-10-20T12:00:00Z"));
        when(materialRepository.findAllByUserIdOrderByCreatedAtDescIdDesc(firstUserId))
                .thenReturn(List.of(newest, older));
        when(materialRepository.findAllByUserIdOrderByCreatedAtDescIdDesc(secondUserId))
                .thenReturn(List.of(otherUserMaterial));

        var firstUserItems = materialService.findAll(firstUser).getItems();
        var secondUserItems = materialService.findAll(secondUser).getItems();

        assertEquals(List.of(newest.getId(), older.getId()), firstUserItems.stream().map(MaterialSummary::getId).toList());
        assertEquals(List.of(otherUserMaterial.getId()), secondUserItems.stream().map(MaterialSummary::getId).toList());
        assertEquals("Newest", firstUserItems.getFirst().getName());
        assertEquals(FileFormat.PDF, firstUserItems.getFirst().getFormat());
        assertEquals(2048L, firstUserItems.getFirst().getSizeBytes());
        assertEquals("Databases", firstUserItems.getFirst().getSubjectName());
        verify(materialRepository).findAllByUserIdOrderByCreatedAtDescIdDesc(firstUserId);
        verify(materialRepository).findAllByUserIdOrderByCreatedAtDescIdDesc(secondUserId);
    }

    private Material material(User user, String title, OffsetDateTime createdAt) {
        Subject subject = new Subject();
        subject.setId(UUID.randomUUID());
        subject.setName("Databases");
        subject.setUser(user);

        Material material = new Material();
        material.setId(UUID.randomUUID());
        material.setUser(user);
        material.setSubject(subject);
        material.setTitle(title);
        material.setFileType("PDF");
        material.setSizeBytes(2048L);
        material.setCreatedAt(createdAt);
        return material;
    }
}
