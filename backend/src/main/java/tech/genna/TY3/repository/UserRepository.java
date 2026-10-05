package tech.genna.TY3.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import tech.genna.TY3.entity.User;
import java.util.Optional;
import java.util.UUID;

public interface UserRepository extends JpaRepository<User, UUID> {
    Optional<User> findByUsername(String username);
    boolean existsByUsername(String username);
    boolean existsByEmail(String email);
    Optional<User> findById(UUID id);
}