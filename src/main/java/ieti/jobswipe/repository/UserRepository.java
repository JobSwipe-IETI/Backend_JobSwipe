package ieti.jobswipe.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import ieti.jobswipe.model.User;

import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {
    Optional<User> findByEmail(String email);

    Optional<User> findByGoogleId(String googleId);

    Optional<User> findFirstByGoogleIdOrEmail(String googleId, String email);
}

