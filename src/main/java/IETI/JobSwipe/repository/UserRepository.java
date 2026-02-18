package ieti.JobSwipe.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import ieti.JobSwipe.model.User;

import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {
    Optional<User> findByEmail(String email);
}
