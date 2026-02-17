package IETI.JobSwipe.repository;

import IETI.JobSwipe.model.Vacancy;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface VacancyRepository extends JpaRepository<Vacancy, Long> {
    List<Vacancy> findAllByCompanyId(Long companyId);
}
