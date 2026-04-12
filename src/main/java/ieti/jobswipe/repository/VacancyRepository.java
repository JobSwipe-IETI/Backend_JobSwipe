package ieti.jobswipe.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import ieti.jobswipe.model.Vacancy;

import java.util.List;

public interface VacancyRepository extends JpaRepository<Vacancy, Long> {
    List<Vacancy> findAllByCompanyId(Long companyId);
}

