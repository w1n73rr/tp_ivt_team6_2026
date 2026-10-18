package tech.genna.TY3.dto.tag;

import java.util.UUID;

public record TagResponse(UUID id, String name, UUID subjectId) {
}
