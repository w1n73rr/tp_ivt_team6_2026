package tech.genna.TY3.service.subject;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tech.genna.TY3.api.model.SubjectResponse;
import tech.genna.TY3.entity.Subject;
import tech.genna.TY3.entity.User;
import tech.genna.TY3.repository.SubjectRepository;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SubjectServiceTest {

    @Mock
    private SubjectRepository subjectRepository;

    @InjectMocks
    private SubjectService subjectService;

    @Test
    void findAllReturnsEmptyListWhenUserHasNoSubjects() {
        User user = User.builder().id(UUID.randomUUID()).build();
        when(subjectRepository.findAllByUserIdOrderByNameAsc(user.getId())).thenReturn(List.of());

        assertEquals(List.of(), subjectService.findAll(user));
    }

    @Test
    void findAllReturnsSubjectsFromRepository() {
        UUID userId = UUID.randomUUID();
        UUID subjectId = UUID.randomUUID();
        User user = User.builder().id(userId).build();
        Subject subject = new Subject();
        subject.setId(subjectId);
        subject.setName("Математика");
        when(subjectRepository.findAllByUserIdOrderByNameAsc(userId)).thenReturn(List.of(subject));

        assertEquals(List.of(new SubjectResponse(subjectId, "Математика")), subjectService.findAll(user));
    }
}
