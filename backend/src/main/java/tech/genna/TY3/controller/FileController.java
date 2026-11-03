package tech.genna.TY3.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.core.io.InputStreamResource;
import org.springframework.core.io.Resource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import tech.genna.TY3.api.FilesApi;
import tech.genna.TY3.entity.Material;
import tech.genna.TY3.repository.MaterialRepository;
import tech.genna.TY3.security.CurrentUser;
import tech.genna.TY3.service.file.FileStorageService;

import java.util.UUID;

@RestController
@RequiredArgsConstructor
public class FileController implements FilesApi {
    private final FileStorageService fileStorageService;
    private final MaterialRepository materials;
    private final CurrentUser currentUser;

    @Override
    public ResponseEntity<Resource> openMaterialFile(UUID materialId) {
        Material material = getOwnedMaterial(materialId);
        if ("DOCX".equals(material.getFileType())) {
            return ResponseEntity.status(HttpStatus.UNSUPPORTED_MEDIA_TYPE).build();
        }
        return streamMaterial(material, true);
    }

    @Override
    public ResponseEntity<Resource> downloadMaterial(UUID materialId) {
        return streamMaterial(getOwnedMaterial(materialId), false);
    }

    @PostMapping(path = "/api/files", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<tech.genna.TY3.dto.file.FileUploadResponse> upload(
            @RequestParam("file") MultipartFile file) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(fileStorageService.uploadFile(file, currentUser.get().getId()));
    }

    @GetMapping("/api/files/{fileId}")
    public ResponseEntity<Resource> read(@PathVariable UUID fileId) {
        var input = fileStorageService.readFile(fileId, currentUser.get().getId());
        return streamedResponse(input, fileId.toString(), false);
    }

    @DeleteMapping("/api/files/{fileId}")
    public ResponseEntity<Void> delete(@PathVariable UUID fileId) {
        fileStorageService.deleteFile(fileId, currentUser.get().getId());
        return ResponseEntity.noContent().build();
    }

    private Material getOwnedMaterial(UUID materialId) {
        return materials.findByIdAndUserId(materialId, currentUser.get().getId())
                .orElseThrow(() -> new org.springframework.web.server.ResponseStatusException(
                        HttpStatus.NOT_FOUND, "NOT_FOUND"));
    }

    private ResponseEntity<Resource> streamMaterial(Material material, boolean inline) {
        var input = fileStorageService.readFile(material.getFileId(), currentUser.get().getId(),
                FileStorageService.FileScope.MATERIALS);
        return streamedResponse(input, material.getOriginalFilename(), inline);
    }

    private ResponseEntity<Resource> streamedResponse(
            software.amazon.awssdk.core.ResponseInputStream<software.amazon.awssdk.services.s3.model.GetObjectResponse> input,
            String filename, boolean inline) {
        String rawContentType = input.response().contentType();
        MediaType contentType;
        try {
            contentType = rawContentType == null ? MediaType.APPLICATION_OCTET_STREAM
                    : MediaType.parseMediaType(rawContentType);
        } catch (IllegalArgumentException exception) {
            contentType = MediaType.APPLICATION_OCTET_STREAM;
        }
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(contentType);
        var disposition = inline ? ContentDisposition.inline() : ContentDisposition.attachment();
        headers.setContentDisposition(disposition.filename(filename).build());
        headers.setCacheControl("private, no-store");
        headers.set("X-Content-Type-Options", "nosniff");
        if (input.response().contentLength() != null) {
            headers.setContentLength(input.response().contentLength());
        }
        return ResponseEntity.ok().headers(headers).body(new InputStreamResource(input));
    }
}
