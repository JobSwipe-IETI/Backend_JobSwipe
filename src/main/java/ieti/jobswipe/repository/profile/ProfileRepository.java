package ieti.jobswipe.repository.profile;

import java.time.LocalDateTime;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import ieti.jobswipe.model.entity.Profile;

public interface ProfileRepository extends JpaRepository<Profile, Long> {
    @Query("SELECT p FROM Profile p WHERE p.user.id = :userId")
    Optional<Profile> findByUserId(@Param("userId") Long userId);

    @EntityGraph(attributePaths = {"user", "candidateProfile"})
    @Query("SELECT p FROM Profile p WHERE p.id = :profileId")
    Optional<Profile> findByIdForAnalysis(@Param("profileId") Long profileId);

    boolean existsByUserId(Long userId);

    @Query("SELECT p.updatedAt FROM Profile p WHERE p.user.id = :userId")
    Optional<LocalDateTime> findUpdatedAtByUserId(@Param("userId") Long userId);
}

