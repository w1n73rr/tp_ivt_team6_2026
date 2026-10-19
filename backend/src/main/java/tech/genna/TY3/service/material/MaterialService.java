package tech.genna.TY3.service.material;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;
import tech.genna.TY3.api.model.*;
import tech.genna.TY3.entity.Material;
import tech.genna.TY3.entity.Subject;
import tech.genna.TY3.entity.Tag;
import tech.genna.TY3.entity.User;
import tech.genna.TY3.repository.MaterialRepository;
import tech.genna.TY3.repository.SubjectRepository;
import tech.genna.TY3.repository.TagRepository;
import tech.genna.TY3.service.file.FileStorageService;
import java.io.ByteArrayInputStream;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.*;

@Service
@RequiredArgsConstructor
public class MaterialService {
    private final MaterialRepository materials;
    private final SubjectRepository subjects;
    private final TagRepository tags;
    private final JdbcTemplate jdbc;
    private final TransactionTemplate transactions;
    private final MaterialFileValidator validator;
    private final FileStorageService storage;
    private final MaterialFileCleanup cleanup;

    public MaterialDetail create(MultipartFile file, UploadMaterialMetadata metadata, User user) {
        validateName(metadata.getName(), 255);
        validateName(metadata.getSubjectName(), 100);
        var validated = validator.validate(file);
        UUID fileId = UUID.randomUUID();
        // Commit the compensation task BEFORE contacting S3. It survives process crashes,
        // upload timeouts, transaction rollback and a failure to acknowledge DB commit.
        cleanup.reserve(fileId, user.getId());
        return transactions.execute(status -> {
            cleanup.lockReservation(fileId);
            lockUser(user);
            Subject subject = resolveSubject(metadata.getSubjectName(), user);
            Material material = new Material();
            material.setUser(user);
            material.setSubject(subject);
            material.setTitle(metadata.getName().strip());
            material.setDescription(metadata.getDescription());
            material.setFileId(fileId);
            material.setOriginalFilename(validated.filename());
            material.setFileType(validated.format());
            material.setSizeBytes(validated.bytes().length);
            material.setCreatedAt(OffsetDateTime.now(ZoneOffset.UTC));
            material.setTags(resolveTags(metadata, subject));
            // Flush constraints before uploading. Neither row nor subject/tags commit on failure.
            materials.saveAndFlush(material);
            var uploaded = storage.uploadFile(new ByteArrayInputStream(validated.bytes()),
                    validated.bytes().length, validated.filename(), validated.contentType(),
                    fileId, user.getId(), FileStorageService.FileScope.MATERIALS);
            material.setOriginalFilename(uploaded.filename());
            material.setSizeBytes(uploaded.size());
            cleanup.cancel(fileId);
            return detail(material);
        });
    }

    @Transactional(readOnly = true)
    public MaterialDetail get(UUID id, User user) {
        return detail(materials.findByIdAndUserId(id, user.getId()).orElseThrow(this::notFound));
    }

    @Transactional
    public MaterialSummary update(UUID id, UpdateMaterialRequest request, User user) {
        lockUser(user);
        Material material = materials.findOwnedForUpdate(id, user.getId()).orElseThrow(this::notFound);
        validateName(request.getName(), 255);
        validateName(request.getSubjectName(), 100);
        Subject subject = resolveSubject(request.getSubjectName(), user);
        if (!material.getSubject().getId().equals(subject.getId())) {
            material.getTags().clear();
            material.setSubject(subject);
        }
        material.setTitle(request.getName().strip());
        materials.flush();
        return summary(material);
    }

    public void delete(UUID id, User user) {
        UUID fileId = transactions.execute(status -> {
            lockUser(user);
            Material material = materials.findOwnedForUpdate(id, user.getId()).orElseThrow(this::notFound);
            if (Set.of("EXTRACTING", "ANALYZING").contains(material.getStatus())) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "MATERIAL_IN_USE");
            }
            // S3 deletion happens only AFTER commit. A rollback leaves the original intact.
            cleanup.enqueue(material.getFileId(), user.getId());
            materials.delete(material);
            materials.flush();
            return material.getFileId();
        });
        cleanup.cleanFile(fileId);
    }

    private void lockUser(User user) {
        jdbc.queryForObject("SELECT id FROM users WHERE id = ? FOR UPDATE", UUID.class, user.getId());
    }

    private Subject resolveSubject(String name, User user) {
        String normalized = name.strip();
        jdbc.update("""
                INSERT INTO subjects(id, user_id, name) VALUES (?, ?, ?)
                ON CONFLICT (user_id, name) DO NOTHING
                """, UUID.randomUUID(), user.getId(), normalized);
        UUID id = jdbc.queryForObject("SELECT id FROM subjects WHERE user_id = ? AND name = ?",
                UUID.class, user.getId(), normalized);
        return subjects.findByIdAndUserId(id, user.getId()).orElseThrow(this::notFound);
    }

    private Set<Tag> resolveTags(UploadMaterialMetadata metadata, Subject subject) {
        Set<UUID> ids = metadata.getTagIds() == null ? Set.of() : metadata.getTagIds();
        Set<String> names = metadata.getNewTagNames() == null ? Set.of() : metadata.getNewTagNames();
        if (ids.size() + names.size() > 20 || new HashSet<>(ids).size() != ids.size()) {
            throw new IllegalArgumentException("At most 20 unique tags are allowed");
        }
        var result = new LinkedHashMap<UUID, Tag>();
        for (UUID id : ids) {
            if (id == null) throw new IllegalArgumentException("Tag id is required");
            Tag tag = tags.findById(id).filter(t -> t.getSubject().getId().equals(subject.getId()))
                    .orElseThrow(this::notFound);
            result.put(id, tag);
        }
        var normalizedNames = new HashSet<String>();
        for (String name : names) {
            if (name == null || name.isBlank() || name.length() > 50 || !normalizedNames.add(name.strip())) {
                throw new IllegalArgumentException("Invalid or duplicate tag name");
            }
            jdbc.update("""
                    INSERT INTO tags(id, subject_id, name) VALUES (?, ?, ?)
                    ON CONFLICT (subject_id, name) DO NOTHING
                    """, UUID.randomUUID(), subject.getId(), name.strip());
            UUID id = jdbc.queryForObject("SELECT id FROM tags WHERE subject_id = ? AND name = ?",
                    UUID.class, subject.getId(), name.strip());
            result.put(id, tags.findById(id).orElseThrow(this::notFound));
        }
        return new LinkedHashSet<>(result.values());
    }

    private MaterialDetail detail(Material material) {
        var materialTags = material.getTags().stream()
                .sorted(Comparator.comparing(Tag::getName).thenComparing(Tag::getId))
                .map(tag -> new TagListItemResponse(tag.getId(), tag.getName())).toList();
        var processing = new MaterialProcessing(material.getId(),
                MaterialProcessing.StatusEnum.fromValue(material.getStatus()),
                "READY".equals(material.getStatus()) ? 100 : 0, material.getCreatedAt());
        return new MaterialDetail(summary(material), material.getOriginalFilename(), materialTags, processing)
                .description(material.getDescription());
    }

    private MaterialSummary summary(Material material) {
        return new MaterialSummary(material.getId(), material.getTitle(),
                FileFormat.fromValue(material.getFileType()), material.getSizeBytes(),
                material.getSubject().getId(), material.getSubject().getName(), material.getCreatedAt());
    }

    private ResponseStatusException notFound() {
        return new ResponseStatusException(HttpStatus.NOT_FOUND, "NOT_FOUND");
    }

    private void validateName(String name, int maxLength) {
        if (name == null || name.isBlank() || name.length() > maxLength) {
            throw new IllegalArgumentException("Invalid name");
        }
    }
}
