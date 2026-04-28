package ieti.jobswipe.repository.recommendation;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import ieti.jobswipe.model.entity.RecommendationCache;

public interface RecommendationCacheRepository extends JpaRepository<RecommendationCache, Long> {

    Optional<RecommendationCache> findByUserIdAndVacancyId(Long userId, Long vacancyId);

    List<RecommendationCache> findByUserIdAndVacancyIdIn(Long userId, List<Long> vacancyIds);

    List<RecommendationCache> findByUserIdInAndVacancyIdIn(List<Long> userIds, List<Long> vacancyIds);

    List<RecommendationCache> findByVacancyIdAndUserIdIn(Long vacancyId, List<Long> userIds);

    long deleteByUpdatedAtBefore(Instant threshold);
    void deleteByVacancyId(Long vacancyId);
}
