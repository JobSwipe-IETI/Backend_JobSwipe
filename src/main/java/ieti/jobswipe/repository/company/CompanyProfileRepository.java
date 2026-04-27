package ieti.jobswipe.repository.company;

import ieti.jobswipe.model.entity.CompanyProfile;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface CompanyProfileRepository extends JpaRepository<CompanyProfile, Long> {
    Optional<CompanyProfile> findByProfileId(Long profileId);
}

