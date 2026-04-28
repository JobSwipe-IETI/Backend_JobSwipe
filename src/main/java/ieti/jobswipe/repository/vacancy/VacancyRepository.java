package ieti.jobswipe.repository.vacancy;

import org.springframework.data.jpa.repository.JpaRepository;

import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import ieti.jobswipe.dto.vacancy.VacancySummaryResponse;
import ieti.jobswipe.model.entity.Vacancy;

import java.util.List;

public interface VacancyRepository extends JpaRepository<Vacancy, Long> {
    List<Vacancy> findAllByCompanyId(Long companyId);

    @Query("""
            SELECT new ieti.jobswipe.dto.vacancy.VacancySummaryResponse(
                v.id,
                v.title,
                c.name,
                v.location,
                v.description,
                v.minSalary,
                v.maxSalary)
            FROM Vacancy v
            JOIN v.company c
            WHERE c.id = :companyId
            """)
    List<VacancySummaryResponse> findSummaryByCompanyId(@Param("companyId") Long companyId);

    @Query("""
            SELECT new ieti.jobswipe.dto.vacancy.VacancySummaryResponse(
                v.id,
                v.title,
                c.name,
                v.location,
                v.description,
                v.minSalary,
                v.maxSalary)
            FROM Vacancy v
            JOIN v.company c
            """)
    List<VacancySummaryResponse> findAllSummaries();

    @Query("""
            SELECT new ieti.jobswipe.dto.vacancy.VacancySummaryResponse(
                v.id,
                v.title,
                c.name,
                v.location,
                v.description,
                v.minSalary,
                v.maxSalary)
            FROM Vacancy v
            JOIN v.company c
            WHERE v.id NOT IN (
                SELECT vs.vacancyId
                FROM VacancySwipe vs
                WHERE vs.userId = :userId
            )
            """)
    List<VacancySummaryResponse> findSummaryNotSwipedByUser(@Param("userId") Long userId);

    @Query("SELECT v FROM Vacancy v WHERE v.id NOT IN (SELECT vs.vacancyId FROM VacancySwipe vs WHERE vs.userId = :userId)")
    List<Vacancy> findAllNotSwipedByUser(@Param("userId") Long userId);
}

