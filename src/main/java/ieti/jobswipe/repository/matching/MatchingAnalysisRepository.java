package ieti.jobswipe.repository.matching;

import ieti.jobswipe.model.entity.MatchingAnalysis;
import ieti.jobswipe.model.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface MatchingAnalysisRepository extends JpaRepository<MatchingAnalysis, Long> {

    Optional<MatchingAnalysis> findByUser(User user);

    @Query("SELECT ma FROM MatchingAnalysis ma WHERE ma.user.isPremium = true AND (ma.lastAnalysisAt IS NULL OR ma.lastAnalysisAt < :cutoffTime) AND ma.status = 'PENDING' ORDER BY ma.updatedAt ASC")
    List<MatchingAnalysis> findPendingPremiumAnalyses(@Param("cutoffTime") LocalDateTime cutoffTime);

    @Query("SELECT ma FROM MatchingAnalysis ma WHERE ma.user.isPremium = true AND ma.status = 'IN_PROGRESS'")
    List<MatchingAnalysis> findInProgressAnalyses();
}
