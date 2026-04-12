package ieti.JobSwipe.service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import ieti.JobSwipe.dto.CreateVacancyRequest;
import ieti.JobSwipe.dto.MatchingResponse;
import ieti.JobSwipe.dto.VacancyRecommendationResponse;
import ieti.JobSwipe.exception.ErrorMessages;
import ieti.JobSwipe.exception.VacancyNotFoundException;
import ieti.JobSwipe.model.EmploymentType;
import ieti.JobSwipe.model.ExperienceLevel;
import ieti.JobSwipe.model.Modality;
import ieti.JobSwipe.model.Role;
import ieti.JobSwipe.model.User;
import ieti.JobSwipe.model.Vacancy;
import ieti.JobSwipe.repository.UserRepository;
import ieti.JobSwipe.repository.VacancyRepository;

@Service
public class VacancyService {

    private static final Logger logger = LoggerFactory.getLogger(VacancyService.class);

    private final VacancyRepository vacancyRepository;
    private final UserRepository userRepository;
    private final MatchingService matchingService;

    public VacancyService(VacancyRepository vacancyRepository,
            UserRepository userRepository,
            MatchingService matchingService) {
        this.vacancyRepository = vacancyRepository;
        this.userRepository = userRepository;
        this.matchingService = matchingService;
    }

    public List<Vacancy> getAllVacancies() {
        return vacancyRepository.findAll();
    }

    public Vacancy getVacancyById(Long id) {
        return vacancyRepository.findById(id)
                .orElseThrow(() -> new VacancyNotFoundException(ErrorMessages.VACANCY_NOT_FOUND));
    }

    public Vacancy createVacancy(CreateVacancyRequest request, Long companyId) {
        User company = userRepository.findById(companyId)
                .orElseThrow(() -> new RuntimeException(ErrorMessages.COMPANY_NOT_FOUND));

        if (company.getRole() != Role.COMPANY) {
            throw new IllegalArgumentException("Only users with COMPANY role can create vacancies");
        }

        Vacancy vacancy = Vacancy.builder()
                .title(request.getTitle())
                .description(request.getDescription())
                .location(request.getLocation())
                .modality(Modality.valueOf(request.getModality()))
                .employmentType(EmploymentType.valueOf(request.getEmploymentType()))
                .experienceLevel(ExperienceLevel.valueOf(request.getExperienceLevel()))
            .sector(request.getSector())
                .technologies(orEmpty(request.getTechnologies()))
                .softSkills(orEmpty(request.getSoftSkills()))
                .responsibilities(orEmpty(request.getResponsibilities()))
                .technicalRequirements(orEmpty(request.getTechnicalRequirements()))
                .minSalary(request.getMinSalary())
                .maxSalary(request.getMaxSalary())
                .benefits(orEmpty(request.getBenefits()))
                .company(company)
                .build();

        return vacancyRepository.save(vacancy);
    }

    public Vacancy updateVacancy(Long id, CreateVacancyRequest request) {
        Vacancy existing = vacancyRepository.findById(id)
                .orElseThrow(() -> new VacancyNotFoundException(ErrorMessages.VACANCY_NOT_FOUND));

        existing.setTitle(request.getTitle());
        existing.setDescription(request.getDescription());
        existing.setLocation(request.getLocation());
        existing.setModality(Modality.valueOf(request.getModality()));
        existing.setEmploymentType(EmploymentType.valueOf(request.getEmploymentType()));
        existing.setExperienceLevel(ExperienceLevel.valueOf(request.getExperienceLevel()));
        existing.setSector(request.getSector());
        existing.setTechnologies(orEmpty(request.getTechnologies()));
        existing.setSoftSkills(orEmpty(request.getSoftSkills()));
        existing.setResponsibilities(orEmpty(request.getResponsibilities()));
        existing.setTechnicalRequirements(orEmpty(request.getTechnicalRequirements()));
        existing.setMinSalary(request.getMinSalary());
        existing.setMaxSalary(request.getMaxSalary());
        existing.setBenefits(orEmpty(request.getBenefits()));

        return vacancyRepository.save(existing);
    }

    public void deleteVacancy(Long id) {
        Vacancy existing = vacancyRepository.findById(id)
                .orElseThrow(() -> new VacancyNotFoundException(ErrorMessages.VACANCY_NOT_FOUND));
        vacancyRepository.delete(existing);
    }

    public List<VacancyRecommendationResponse> getRecommendedVacancies(Long userId, Float minScore, Integer limit) {
        float effectiveMinScore = minScore != null ? minScore : 0f;
        int effectiveLimit = limit != null ? limit : 20;

        List<Vacancy> vacancies = vacancyRepository.findAll();
        List<VacancyRecommendationResponse> recommendations = new ArrayList<>();

        for (Vacancy vacancy : vacancies) {
            try {
                MatchingResponse match = matchingService.calculateMatch(userId, vacancy.getId());
                Float compatibilityPercentage = match.getCompatibilityPercentage();
                float score = compatibilityPercentage != null ? compatibilityPercentage.floatValue() : 0f;

                if (score >= effectiveMinScore) {
                    recommendations.add(VacancyRecommendationResponse.builder()
                            .vacancyId(vacancy.getId())
                            .title(vacancy.getTitle())
                            .location(vacancy.getLocation())
                            .compatibilityPercentage(match.getCompatibilityPercentage())
                            .compatibilityLevel(match.getCompatibilityLevel())
                            .similarityScore(match.getSimilarityScore())
                            .feedback(match.getFeedback())
                            .build());
                }
            } catch (RuntimeException ex) {
                logger.warn("Skipping vacancy {} due to matching error: {}", vacancy.getId(), ex.getMessage());
            }
        }

        recommendations.sort(
                Comparator.comparing(
                        VacancyRecommendationResponse::getCompatibilityPercentage,
                        Comparator.nullsLast(Float::compareTo))
                        .reversed());

        if (recommendations.size() > effectiveLimit) {
            return recommendations.subList(0, effectiveLimit);
        }

        return recommendations;
    }

    private List<String> orEmpty(List<String> list) {
        return list != null ? list : new ArrayList<>();
    }
}
