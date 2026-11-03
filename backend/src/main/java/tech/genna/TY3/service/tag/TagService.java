package tech.genna.TY3.service.tag;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import tech.genna.TY3.api.model.CreateTagRequest;
import tech.genna.TY3.api.model.TagListItemResponse;
import tech.genna.TY3.api.model.TagResponse;
import tech.genna.TY3.entity.Subject;
import tech.genna.TY3.entity.Tag;
import tech.genna.TY3.entity.User;
import tech.genna.TY3.repository.SubjectRepository;
import tech.genna.TY3.repository.TagRepository;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class TagService {

    private final SubjectRepository subjectRepository;
    private final TagRepository tagRepository;

    @Transactional(readOnly = true)
    public List<TagListItemResponse> findAll(User user) {
        return tagRepository.findAllBySubject_User_IdOrderByNameAsc(user.getId()).stream()
                .map(tag -> new TagListItemResponse(tag.getId(), tag.getName()))
                .toList();
    }

    @Transactional
    public TagResponse create(UUID subjectId, CreateTagRequest request, User user) {
        Subject subject = subjectRepository.findByIdAndUserId(subjectId, user.getId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Subject not found"));
        String name = request.getName().trim();
        if (tagRepository.existsBySubjectIdAndName(subjectId, name)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "TAG_ALREADY_EXISTS");
        }

        Tag tag = new Tag();
        tag.setName(name);
        tag.setSubject(subject);
        Tag saved = tagRepository.save(tag);
        return new TagResponse(saved.getId(), saved.getName(), subjectId);
    }
}
