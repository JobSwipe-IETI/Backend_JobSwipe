package ieti.jobswipe.repository.profile;

import ieti.jobswipe.model.entity.CandidateProfile;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface CandidateProfileRepository extends JpaRepository<CandidateProfile, Long> {
    Optional<CandidateProfile> findByProfileId(Long profileId);
}

