package ieti.jobswipe.repository;

import ieti.jobswipe.model.CompanyProfile;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface CompanyProfileRepository extends JpaRepository<CompanyProfile, Long> {
    Optional<CompanyProfile> findByProfileId(Long profileId);
}

