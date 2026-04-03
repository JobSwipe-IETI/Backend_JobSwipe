package ieti.JobSwipe.service;

import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import ieti.JobSwipe.dto.CreateExtendedVacancyRequest;
import ieti.JobSwipe.model.Role;
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
    private final DocumentStorageService documentStorageService;

    public VacancyService(VacancyRepository vacancyRepository,
            UserRepository userRepository,
            DocumentStorageService documentStorageService) {
        this.vacancyRepository = vacancyRepository;
        this.userRepository = userRepository;
        this.documentStorageService = documentStorageService;
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

    public Vacancy createExtendedVacancy(CreateExtendedVacancyRequest request,
            Long companyId,
            MultipartFile document) {
        User company = userRepository.findById(companyId)
                .orElseThrow(() -> new RuntimeException(ErrorMessages.COMPANY_NOT_FOUND));

        if (company.getRole() != Role.COMPANY) {
            throw new IllegalArgumentException("Only users with COMPANY role can create vacancies");
        }

        DocumentStorageService.StoredDocument storedDocument = null;
        if (document != null && !document.isEmpty()) {
            storedDocument = documentStorageService.store(document);
        }

        Vacancy vacancy = Vacancy.builder()
                .title(request.getPositionRequested())
                .description(buildExtendedDescription(request, storedDocument))
                .salary(request.getDesiredMonthlySalary())
                .company(company)
                .build();

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

    private String buildExtendedDescription(CreateExtendedVacancyRequest request,
            DocumentStorageService.StoredDocument storedDocument) {
        StringBuilder description = new StringBuilder();
        description.append(request.getVacancySummary());
        description.append("\n\n--- Extended profile data ---");
        description.append("\nApplication date: ").append(request.getApplicationDate());
        description.append("\nCandidate full name: ").append(request.getCandidateFullName());
        description.append("\nPhone: ").append(request.getPhoneNumber());
        description.append("\nEmail: ").append(request.getEmail());
        description.append("\nAcademic level: ").append(request.getAcademicLevel());
        description.append("\nPrevious employment: ").append(request.getPreviousEmploymentData());
        description.append("\nResponsibilities: ").append(request.getResponsibilities());
        description.append("\nLanguages: ").append(request.getLanguages());
        description.append("\nSoftware and machinery: ").append(request.getSoftwareAndMachinery());
        description.append("\nSoft skills: ").append(request.getSoftSkills());
        description.append("\nReferences: ").append(request.getPersonalAndWorkReferences());

        if (storedDocument != null) {
            description.append("\nAttached document: ").append(storedDocument.storagePath());
        } else if (request.getDocumentAttachments() != null && !request.getDocumentAttachments().isBlank()) {
            description.append("\nAttached document: ").append(request.getDocumentAttachments());
        }

        return description.toString();
    }
}
