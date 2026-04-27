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

    void deleteByVacancyId(Long vacancyId);

    Optional<VacancySwipe> findByUserIdAndVacancyId(Long userId, Long vacancyId);

    boolean existsByUserIdAndVacancyIdAndDecision(Long userId, Long vacancyId, SwipeDecisionType decision);

        List<VacancySwipe> findByUserIdAndVacancyIdInAndDecision(Long userId, List<Long> vacancyIds, SwipeDecisionType decision);

        List<VacancySwipe> findByUserIdInAndVacancyIdInAndDecision(List<Long> userIds, List<Long> vacancyIds,
            SwipeDecisionType decision);

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

        @Query("SELECT vs.vacancyId, COUNT(vs) FROM VacancySwipe vs WHERE vs.vacancyId IN :vacancyIds AND vs.decision = :decision GROUP BY vs.vacancyId")
        List<Object[]> countByVacancyIdsAndDecision(@Param("vacancyIds") List<Long> vacancyIds,
            @Param("decision") SwipeDecisionType decision);

        @Query(value = """
            SELECT vs.vacancy_id, COUNT(*)
            FROM vacancy_swipes vs
            WHERE vs.vacancy_id IN (:vacancyIds)
              AND vs.decision = 'LIKE'
              AND NOT EXISTS (
                SELECT 1
                FROM company_candidate_decisions ccd
                WHERE ccd.company_id = :companyId
                  AND ccd.vacancy_id = vs.vacancy_id
                  AND ccd.candidate_id = vs.user_id
              )
            GROUP BY vs.vacancy_id
            """, nativeQuery = true)
        List<Object[]> countPendingLikesByVacancyIds(
            @Param("companyId") Long companyId,
            @Param("vacancyIds") List<Long> vacancyIds);

        @Query(value = """
            SELECT
                vs.vacancy_id AS vacancyId,
                v.title AS vacancyTitle,
                v.company_id AS companyId,
                u.name AS companyName,
                vs.updated_at AS appliedAt,
                ccd.decision AS decision,
                ccd.updated_at AS decisionAt,
                CASE WHEN ccd.decision = 'LIKE' THEN TRUE ELSE FALSE END AS matched,
                ccd.rejection_reason AS rejectionReason,
                ccd.rejection_tags AS rejectionTags,
                ccd.missing_technologies AS missingTechnologies,
                ccd.missing_responsibilities AS missingResponsibilities,
                ccd.missing_technical_requirements AS missingTechnicalRequirements,
                ccd.expected_experience_level AS expectedExperienceLevel,
                ccd.ai_summary AS aiSummary,
                ccd.rejection_comment AS rejectionComment
            FROM vacancy_swipes vs
            JOIN vacancies v ON v.id = vs.vacancy_id
            LEFT JOIN users u ON u.id = v.company_id
            LEFT JOIN company_candidate_decisions ccd
                ON ccd.vacancy_id = vs.vacancy_id
               AND ccd.candidate_id = vs.user_id
               AND ccd.company_id = v.company_id
            WHERE vs.user_id = :candidateId
              AND vs.decision = 'LIKE'
            ORDER BY vs.updated_at DESC
            LIMIT :limit
            """, nativeQuery = true)
        List<CandidateApplicationProjection> findCandidateApplications(
            @Param("candidateId") Long candidateId,
            @Param("limit") int limit);
}
