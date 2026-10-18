package tech.genna.TY3.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import tech.genna.TY3.entity.Subject;

import java.util.Optional;
import java.util.UUID;

public interface SubjectRepository extends JpaRepository<Subject, UUID> {
    boolean existsByUserIdAndName(UUID userId, String name);
    Optional<Subject> findByIdAndUserId(UUID id, UUID userId);
}
