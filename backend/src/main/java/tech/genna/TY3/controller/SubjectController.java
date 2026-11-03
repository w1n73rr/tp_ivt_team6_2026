package tech.genna.TY3.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;
import tech.genna.TY3.api.SubjectsApi;
import tech.genna.TY3.api.model.CreateSubjectRequest;
import tech.genna.TY3.api.model.SubjectListResponse;
import tech.genna.TY3.api.model.SubjectResponse;
import tech.genna.TY3.security.CurrentUser;
import tech.genna.TY3.service.subject.SubjectService;

@RestController
@RequiredArgsConstructor
public class SubjectController implements SubjectsApi {

    private final SubjectService subjectService;
    private final CurrentUser currentUser;

    @Override
    public ResponseEntity<SubjectListResponse> listSubjects() {
        return ResponseEntity.ok(new SubjectListResponse(subjectService.findAll(currentUser.get())));
    }

    @Override
    public ResponseEntity<SubjectResponse> createSubject(CreateSubjectRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(subjectService.create(request, currentUser.get()));
    }
}
