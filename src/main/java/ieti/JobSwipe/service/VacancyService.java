package ieti.JobSwipe.service;

import org.springframework.stereotype.Service;

import ieti.JobSwipe.model.User;
import ieti.JobSwipe.model.Vacancy;
import ieti.JobSwipe.repository.UserRepository;
import ieti.JobSwipe.repository.VacancyRepository;
import ieti.JobSwipe.exception.ErrorMessages;
import ieti.JobSwipe.exception.VacancyNotFoundException;

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
                .orElseThrow(() -> new VacancyNotFoundException(ErrorMessages.VACANCY_NOT_FOUND));
    }

    public Vacancy createVacancy(Vacancy vacancy, Long companyId) {
        User company = userRepository.findById(companyId)
                .orElseThrow(() -> new RuntimeException(ErrorMessages.COMPANY_NOT_FOUND));

        vacancy.setCompany(company);
        return vacancyRepository.save(vacancy);
    }

    public Vacancy updateVacancy(Long id, Vacancy vacancy) {
        Vacancy existingVacancy = vacancyRepository.findById(id)
                .orElseThrow(() -> new VacancyNotFoundException(ErrorMessages.VACANCY_NOT_FOUND));

        existingVacancy.setTitle(vacancy.getTitle());
        existingVacancy.setDescription(vacancy.getDescription());
        existingVacancy.setSalary(vacancy.getSalary());

        return vacancyRepository.save(existingVacancy);
    }

    public void deleteVacancy(Long id) {
        Vacancy existingVacancy = vacancyRepository.findById(id)
                .orElseThrow(() -> new VacancyNotFoundException(ErrorMessages.VACANCY_NOT_FOUND));
        vacancyRepository.delete(existingVacancy);
    }
}
