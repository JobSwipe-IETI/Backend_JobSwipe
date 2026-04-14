package ieti.jobswipe.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import ieti.jobswipe.model.SwipeDecisionType;
import ieti.jobswipe.model.VacancySwipe;

public interface VacancySwipeRepository extends JpaRepository<VacancySwipe, Long> {

    Optional<VacancySwipe> findByUserIdAndVacancyId(Long userId, Long vacancyId);

    @Query("SELECT vs.vacancyId FROM VacancySwipe vs WHERE vs.userId = :userId")
    List<Long> findSwipedVacancyIdsByUserId(@Param("userId") Long userId);

    @Query("SELECT vs FROM VacancySwipe vs WHERE vs.vacancyId IN :vacancyIds AND vs.decision = :decision ORDER BY vs.updatedAt DESC")
    List<VacancySwipe> findRecentByVacancyIdsAndDecision(
            @Param("vacancyIds") List<Long> vacancyIds,
            @Param("decision") SwipeDecisionType decision,
            Pageable pageable);

        List<VacancySwipe> findByVacancyIdAndDecisionOrderByUpdatedAtDesc(
            Long vacancyId,
            SwipeDecisionType decision,
            Pageable pageable);

        long countByVacancyIdAndDecision(Long vacancyId, SwipeDecisionType decision);
}
