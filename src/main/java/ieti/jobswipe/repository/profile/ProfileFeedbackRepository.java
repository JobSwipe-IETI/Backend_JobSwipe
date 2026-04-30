package ieti.jobswipe.repository.profile;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import ieti.jobswipe.model.entity.Profile;
import ieti.jobswipe.model.entity.ProfileFeedback;

public interface ProfileFeedbackRepository extends JpaRepository<ProfileFeedback, Long> {
    Optional<ProfileFeedback> findByProfile(Profile profile);

    @Query("SELECT pf.payload FROM ProfileFeedback pf WHERE pf.profile.id = :profileId")
    Optional<String> findPayloadByProfileId(@Param("profileId") Long profileId);
}
