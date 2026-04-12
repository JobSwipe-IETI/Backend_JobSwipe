package ieti.jobswipe.repository;

import ieti.jobswipe.model.Profile;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Optional;

public interface ProfileRepository extends JpaRepository<Profile, Long> {
    @Query("SELECT p FROM Profile p WHERE p.user.id = :userId")
    Optional<Profile> findByUserId(@Param("userId") Long userId);

    @Query("SELECT p.updatedAt FROM Profile p WHERE p.user.id = :userId")
    Optional<LocalDateTime> findUpdatedAtByUserId(@Param("userId") Long userId);
}

