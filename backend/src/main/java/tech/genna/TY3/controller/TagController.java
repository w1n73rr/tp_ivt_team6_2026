package tech.genna.TY3.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;
import tech.genna.TY3.api.TagsApi;
import tech.genna.TY3.api.model.CreateTagRequest;
import tech.genna.TY3.api.model.TagListResponse;
import tech.genna.TY3.api.model.TagResponse;
import tech.genna.TY3.security.CurrentUser;
import tech.genna.TY3.service.tag.TagService;

import java.util.UUID;

@RestController
@RequiredArgsConstructor
public class TagController implements TagsApi {

    private final TagService tagService;
    private final CurrentUser currentUser;

    @Override
    public ResponseEntity<TagListResponse> listTags() {
        return ResponseEntity.ok(new TagListResponse(tagService.findAll(currentUser.get())));
    }

    @Override
    public ResponseEntity<TagResponse> createTag(UUID subjectId, CreateTagRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(tagService.create(subjectId, request, currentUser.get()));
    }
}
