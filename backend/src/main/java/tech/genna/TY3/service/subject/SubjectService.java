package tech.genna.TY3.service.subject;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import tech.genna.TY3.api.model.CreateSubjectRequest;
import tech.genna.TY3.api.model.SubjectResponse;
import tech.genna.TY3.entity.Subject;
import tech.genna.TY3.entity.User;
import tech.genna.TY3.repository.SubjectRepository;

import java.util.List;

@Service
@RequiredArgsConstructor
public class SubjectService {

    private final SubjectRepository subjectRepository;

    @Transactional(readOnly = true)
    public List<SubjectResponse> findAll(User user) {
        return subjectRepository.findAllByUserIdOrderByNameAsc(user.getId()).stream()
                .map(subject -> new SubjectResponse(subject.getId(), subject.getName()))
                .toList();
    }

    @Transactional
    public SubjectResponse create(CreateSubjectRequest request, User user) {
        String name = request.getName().trim();
        if (subjectRepository.existsByUserIdAndName(user.getId(), name)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "SUBJECT_ALREADY_EXISTS");
        }

        Subject subject = new Subject();
        subject.setName(name);
        subject.setUser(user);
        Subject saved = subjectRepository.save(subject);
        return new SubjectResponse(saved.getId(), saved.getName());
    }
}
