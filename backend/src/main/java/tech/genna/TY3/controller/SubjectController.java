package tech.genna.TY3.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import tech.genna.TY3.dto.subject.CreateSubjectRequest;
import tech.genna.TY3.dto.subject.SubjectResponse;
import tech.genna.TY3.entity.User;
import tech.genna.TY3.service.subject.SubjectService;

import java.util.List;

@RestController
@RequestMapping("/api/subjects")
@RequiredArgsConstructor
public class SubjectController {

    private final SubjectService subjectService;

    @GetMapping
    public List<SubjectResponse> findAll(@AuthenticationPrincipal User user) {
        return subjectService.findAll(user);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public SubjectResponse create(@Valid @RequestBody CreateSubjectRequest request,
                                  @AuthenticationPrincipal User user) {
        return subjectService.create(request, user);
    }
}
