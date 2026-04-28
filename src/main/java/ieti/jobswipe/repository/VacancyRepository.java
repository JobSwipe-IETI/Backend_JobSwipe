package ieti.jobswipe.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import ieti.jobswipe.model.Vacancy;

import java.util.List;

public interface VacancyRepository extends JpaRepository<Vacancy, Long> {
    List<Vacancy> findAllByCompanyId(Long companyId);

    @Query("SELECT v FROM Vacancy v WHERE v.id NOT IN (SELECT vs.vacancyId FROM VacancySwipe vs WHERE vs.userId = :userId)")
    List<Vacancy> findAllNotSwipedByUser(@Param("userId") Long userId);
}

