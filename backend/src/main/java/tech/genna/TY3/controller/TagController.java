package tech.genna.TY3.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import tech.genna.TY3.dto.tag.CreateTagRequest;
import tech.genna.TY3.dto.tag.TagResponse;
import tech.genna.TY3.entity.User;
import tech.genna.TY3.service.tag.TagService;

import java.util.UUID;

@RestController
@RequestMapping("/api/subjects/{subjectId}/tags")
@RequiredArgsConstructor
public class TagController {

    private final TagService tagService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public TagResponse create(@PathVariable UUID subjectId,
                              @Valid @RequestBody CreateTagRequest request,
                              @AuthenticationPrincipal User user) {
        return tagService.create(subjectId, request, user);
    }
}
