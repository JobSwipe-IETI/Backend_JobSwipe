package ieti.jobswipe.repository;

import ieti.jobswipe.model.CandidateProfile;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface CandidateProfileRepository extends JpaRepository<CandidateProfile, Long> {
    Optional<CandidateProfile> findByProfileId(Long profileId);
}

