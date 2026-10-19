package tech.genna.TY3.dto.file;

import java.util.UUID;

public record FileUploadResponse(UUID fileId, String objectKey, String filename,
                                 String contentType, long size) {
}
