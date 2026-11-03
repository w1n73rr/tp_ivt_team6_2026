package tech.genna.TY3.repository;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import tech.genna.TY3.entity.Material;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface MaterialRepository extends JpaRepository<Material, UUID> {
    @EntityGraph(attributePaths = "subject")
    List<Material> findAllByUserIdOrderByCreatedAtDescIdDesc(UUID userId);

    Optional<Material> findByIdAndUserId(UUID id, UUID userId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select m from Material m where m.id = :id and m.user.id = :userId")
    Optional<Material> findOwnedForUpdate(UUID id, UUID userId);
}
