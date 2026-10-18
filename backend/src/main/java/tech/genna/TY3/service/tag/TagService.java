package tech.genna.TY3.service.tag;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import tech.genna.TY3.dto.tag.CreateTagRequest;
import tech.genna.TY3.dto.tag.TagResponse;
import tech.genna.TY3.entity.Subject;
import tech.genna.TY3.entity.Tag;
import tech.genna.TY3.entity.User;
import tech.genna.TY3.repository.SubjectRepository;
import tech.genna.TY3.repository.TagRepository;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class TagService {

    private final SubjectRepository subjectRepository;
    private final TagRepository tagRepository;

    @Transactional
    public TagResponse create(UUID subjectId, CreateTagRequest request, User user) {
        Subject subject = subjectRepository.findByIdAndUserId(subjectId, user.getId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Subject not found"));
        String name = request.name().trim();
        if (tagRepository.existsBySubjectIdAndName(subjectId, name)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Tag already exists");
        }

        Tag tag = new Tag();
        tag.setName(name);
        tag.setSubject(subject);
        Tag saved = tagRepository.save(tag);
        return new TagResponse(saved.getId(), saved.getName(), subjectId);
    }
}
