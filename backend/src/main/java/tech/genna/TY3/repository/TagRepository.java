package tech.genna.TY3.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import tech.genna.TY3.entity.Tag;

import java.util.UUID;

public interface TagRepository extends JpaRepository<Tag, UUID> {
    boolean existsBySubjectIdAndName(UUID subjectId, String name);
}
