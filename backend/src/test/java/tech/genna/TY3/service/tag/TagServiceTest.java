package tech.genna.TY3.service.tag;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tech.genna.TY3.api.model.TagListItemResponse;
import tech.genna.TY3.entity.Subject;
import tech.genna.TY3.entity.Tag;
import tech.genna.TY3.entity.User;
import tech.genna.TY3.repository.SubjectRepository;
import tech.genna.TY3.repository.TagRepository;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TagServiceTest {

    @Mock
    private SubjectRepository subjectRepository;

    @Mock
    private TagRepository tagRepository;

    @InjectMocks
    private TagService tagService;

    @Test
    void findAllReturnsEmptyListWhenUserHasNoTags() {
        User user = User.builder().id(UUID.randomUUID()).build();
        when(tagRepository.findAllBySubject_User_IdOrderByNameAsc(user.getId())).thenReturn(List.of());

        assertEquals(List.of(), tagService.findAll(user));
    }

    @Test
    void findAllReturnsTagsFromRepository() {
        UUID userId = UUID.randomUUID();
        UUID subjectId = UUID.randomUUID();
        UUID tagId = UUID.randomUUID();
        User user = User.builder().id(userId).build();
        Subject subject = new Subject();
        subject.setId(subjectId);
        Tag tag = new Tag();
        tag.setId(tagId);
        tag.setName("Алгебра");
        tag.setSubject(subject);
        when(tagRepository.findAllBySubject_User_IdOrderByNameAsc(userId)).thenReturn(List.of(tag));

        assertEquals(List.of(new TagListItemResponse(tagId, "Алгебра")), tagService.findAll(user));
    }
}
