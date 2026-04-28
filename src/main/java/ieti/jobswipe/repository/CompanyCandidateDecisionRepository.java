package ieti.jobswipe.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import ieti.jobswipe.model.CompanyCandidateDecision;
import ieti.jobswipe.model.SwipeDecisionType;

public interface CompanyCandidateDecisionRepository extends JpaRepository<CompanyCandidateDecision, Long> {

    Optional<CompanyCandidateDecision> findByCompanyIdAndCandidateIdAndVacancyId(Long companyId, Long candidateId,
            Long vacancyId);

    List<CompanyCandidateDecision> findByCandidateIdAndDecisionOrderByUpdatedAtDesc(Long candidateId,
            SwipeDecisionType decision, Pageable pageable);

    List<CompanyCandidateDecision> findByCompanyIdAndDecisionOrderByUpdatedAtDesc(Long companyId,
            SwipeDecisionType decision, Pageable pageable);

    List<CompanyCandidateDecision> findByCompanyIdAndVacancyIdIn(Long companyId, List<Long> vacancyIds);

    List<CompanyCandidateDecision> findByCompanyIdAndVacancyIdAndCandidateIdIn(
            Long companyId,
            Long vacancyId,
            List<Long> candidateIds);

                @Query(value = """
                                                SELECT
                                                                ccd.vacancy_id AS vacancyId,
                                                                v.title AS vacancyTitle,
                                                                ccd.company_id AS counterpartId,
                                                                u.name AS counterpartName,
                                                                ccd.updated_at AS matchedAt,
                                                                rc.compatibility_percentage AS compatibilityPercentage,
                                                                rc.compatibility_level AS compatibilityLevel
                                                FROM company_candidate_decisions ccd
                                                JOIN vacancy_swipes vs
                                                        ON vs.user_id = ccd.candidate_id
                                                 AND vs.vacancy_id = ccd.vacancy_id
                                                 AND vs.decision = 'LIKE'
                                                LEFT JOIN vacancies v ON v.id = ccd.vacancy_id
                                                LEFT JOIN users u ON u.id = ccd.company_id
                                                LEFT JOIN recommendation_cache rc
                                                        ON rc.user_id = ccd.candidate_id
                                                 AND rc.vacancy_id = ccd.vacancy_id
                                                WHERE ccd.candidate_id = :candidateId
                                                        AND ccd.decision = 'LIKE'
                                                ORDER BY ccd.updated_at DESC
                                                LIMIT :limit
                                                """, nativeQuery = true)
                List<UserMatchProjection> findCandidateMatches(@Param("candidateId") Long candidateId, @Param("limit") int limit);

                @Query(value = """
                                                SELECT
                                                                ccd.vacancy_id AS vacancyId,
                                                                v.title AS vacancyTitle,
                                                                ccd.candidate_id AS counterpartId,
                                                                u.name AS counterpartName,
                                                                ccd.updated_at AS matchedAt,
                                                                rc.compatibility_percentage AS compatibilityPercentage,
                                                                rc.compatibility_level AS compatibilityLevel
                                                FROM company_candidate_decisions ccd
                                                JOIN vacancy_swipes vs
                                                        ON vs.user_id = ccd.candidate_id
                                                 AND vs.vacancy_id = ccd.vacancy_id
                                                 AND vs.decision = 'LIKE'
                                                LEFT JOIN vacancies v ON v.id = ccd.vacancy_id
                                                LEFT JOIN users u ON u.id = ccd.candidate_id
                                                LEFT JOIN recommendation_cache rc
                                                        ON rc.user_id = ccd.candidate_id
                                                 AND rc.vacancy_id = ccd.vacancy_id
                                                WHERE ccd.company_id = :companyId
                                                        AND ccd.decision = 'LIKE'
                                                ORDER BY ccd.updated_at DESC
                                                LIMIT :limit
                                                """, nativeQuery = true)
                List<UserMatchProjection> findCompanyMatches(@Param("companyId") Long companyId, @Param("limit") int limit);
}