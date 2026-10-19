package tech.genna.TY3.controller;

import lombok.RequiredArgsConstructor;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import tech.genna.TY3.dto.file.FileUploadResponse;
import tech.genna.TY3.entity.User;
import tech.genna.TY3.service.file.FileStorageService;

import java.util.UUID;
import java.io.IOException;

@RestController
@RequestMapping("/api/files")
@RequiredArgsConstructor
public class FileController {
    private final FileStorageService fileStorageService;

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<FileUploadResponse> upload(@RequestParam("file") MultipartFile file,
                                                    @AuthenticationPrincipal User user) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(fileStorageService.uploadFile(file, user.getId()));
    }

    @GetMapping("/{fileId}")
    public void read(@PathVariable UUID fileId, @AuthenticationPrincipal User user,
                     HttpServletResponse response) throws IOException {
        // Synchronous streaming keeps memory usage independent of the file size.
        try (var input = fileStorageService.readFile(fileId, user.getId())) {
            String contentType = input.response().contentType();
            MediaType mediaType;
            try {
                mediaType = contentType == null ? MediaType.APPLICATION_OCTET_STREAM
                        : MediaType.parseMediaType(contentType);
            } catch (IllegalArgumentException e) {
                mediaType = MediaType.APPLICATION_OCTET_STREAM;
            }
            response.setContentType(mediaType.toString());
            if (input.response().contentLength() != null) {
                response.setContentLengthLong(input.response().contentLength());
            }
            response.setHeader(HttpHeaders.CONTENT_DISPOSITION,
                    ContentDisposition.attachment().filename(fileId.toString()).build().toString());
            response.setHeader(HttpHeaders.CACHE_CONTROL, "no-store");
            response.setHeader("X-Content-Type-Options", "nosniff");
            input.transferTo(response.getOutputStream());
        }
    }

    @DeleteMapping("/{fileId}")
    public ResponseEntity<Void> delete(@PathVariable UUID fileId, @AuthenticationPrincipal User user) {
        fileStorageService.deleteFile(fileId, user.getId());
        return ResponseEntity.noContent().build();
    }
}
