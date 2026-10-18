package tech.genna.TY3.service.subject;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import tech.genna.TY3.dto.subject.CreateSubjectRequest;
import tech.genna.TY3.dto.subject.SubjectResponse;
import tech.genna.TY3.entity.Subject;
import tech.genna.TY3.entity.User;
import tech.genna.TY3.repository.SubjectRepository;

@Service
@RequiredArgsConstructor
public class SubjectService {

    private final SubjectRepository subjectRepository;

    @Transactional
    public SubjectResponse create(CreateSubjectRequest request, User user) {
        String name = request.name().trim();
        if (subjectRepository.existsByUserIdAndName(user.getId(), name)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Subject already exists");
        }

        Subject subject = new Subject();
        subject.setName(name);
        subject.setUser(user);
        Subject saved = subjectRepository.save(subject);
        return new SubjectResponse(saved.getId(), saved.getName());
    }
}
