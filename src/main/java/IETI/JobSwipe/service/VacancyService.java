package IETI.JobSwipe.service;

import IETI.JobSwipe.model.User;
import IETI.JobSwipe.model.Vacancy;
import IETI.JobSwipe.repository.UserRepository;
import IETI.JobSwipe.repository.VacancyRepository;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class VacancyService {

    private final VacancyRepository vacancyRepository;
    private final UserRepository userRepository;

    public VacancyService(VacancyRepository vacancyRepository, UserRepository userRepository) {
        this.vacancyRepository = vacancyRepository;
        this.userRepository = userRepository;
    }

    public List<Vacancy> getAllVacancies() {
        return vacancyRepository.findAll();
    }

    public Vacancy getVacancyById(Long id) {
        return vacancyRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Vacancy not found"));
    }

    public Vacancy createVacancy(Vacancy vacancy, Long companyId) {
        User company = userRepository.findById(companyId)
                .orElseThrow(() -> new RuntimeException("Company not found"));

        vacancy.setCompany(company);
        return vacancyRepository.save(vacancy);
    }

    public Vacancy updateVacancy(Long id, Vacancy vacancy) {
        Vacancy existingVacancy = vacancyRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Vacancy not found"));

        existingVacancy.setTitle(vacancy.getTitle());
        existingVacancy.setDescription(vacancy.getDescription());
        existingVacancy.setSalary(vacancy.getSalary());

        return vacancyRepository.save(existingVacancy);
    }

    public void deleteVacancy(Long id) {
        Vacancy existingVacancy = vacancyRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Vacancy not found"));
        vacancyRepository.delete(existingVacancy);
    }
}
