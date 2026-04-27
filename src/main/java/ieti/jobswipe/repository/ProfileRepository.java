package ieti.jobswipe.repository;

import java.time.LocalDateTime;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import ieti.jobswipe.model.Profile;

public interface ProfileRepository extends JpaRepository<Profile, Long> {
    @Query("SELECT p FROM Profile p WHERE p.user.id = :userId")
    Optional<Profile> findByUserId(@Param("userId") Long userId);

    boolean existsByUserId(Long userId);

    @Query("SELECT p.updatedAt FROM Profile p WHERE p.user.id = :userId")
    Optional<LocalDateTime> findUpdatedAtByUserId(@Param("userId") Long userId);
}

