package ieti.jobswipe.service;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import ieti.jobswipe.dto.CreateVacancyRequest;
import ieti.jobswipe.dto.MatchingResponse;
import ieti.jobswipe.dto.VacancyRecommendationResponse;
import ieti.jobswipe.exception.ErrorMessages;
import ieti.jobswipe.exception.VacancyNotFoundException;
import ieti.jobswipe.model.EmploymentType;
import ieti.jobswipe.model.ExperienceLevel;
import ieti.jobswipe.model.Modality;
import ieti.jobswipe.model.RecommendationCache;
import ieti.jobswipe.model.Role;
import ieti.jobswipe.model.SwipeDecisionType;
import ieti.jobswipe.model.User;
import ieti.jobswipe.model.Vacancy;
import ieti.jobswipe.model.VacancySwipe;
import ieti.jobswipe.repository.ProfileRepository;
import ieti.jobswipe.repository.RecommendationCacheRepository;
import ieti.jobswipe.repository.UserRepository;
import ieti.jobswipe.repository.VacancyRepository;
import ieti.jobswipe.repository.VacancySwipeRepository;

@Service
public class VacancyService {

    @FunctionalInterface
    public interface RecommendationProgressListener {
        void onProgress(int processed, int total, String message);
    }

    private static final Logger logger = LoggerFactory.getLogger(VacancyService.class);

    private final VacancyRepository vacancyRepository;
    private final UserRepository userRepository;
    private final MatchingService matchingService;
    private final ProfileRepository profileRepository;
    private final RecommendationCacheRepository recommendationCacheRepository;
    private final VacancySwipeRepository vacancySwipeRepository;

    @Value("${app.recommendations.cache.ttl-seconds:1800}")
    private long recommendationCacheTtlSeconds;

    public VacancyService(VacancyRepository vacancyRepository,
            UserRepository userRepository,
            MatchingService matchingService,
            ProfileRepository profileRepository,
            RecommendationCacheRepository recommendationCacheRepository,
            VacancySwipeRepository vacancySwipeRepository) {
        this.vacancyRepository = vacancyRepository;
        this.userRepository = userRepository;
        this.matchingService = matchingService;
        this.profileRepository = profileRepository;
        this.recommendationCacheRepository = recommendationCacheRepository;
        this.vacancySwipeRepository = vacancySwipeRepository;
    }

    public List<Vacancy> getAllVacancies() {
        return vacancyRepository.findAll();
    }

    public List<Vacancy> getAllVacanciesForUser(Long userId) {
        Set<Long> swipedVacancyIds = new HashSet<>(vacancySwipeRepository.findSwipedVacancyIdsByUserId(userId));
        if (swipedVacancyIds.isEmpty()) {
            return vacancyRepository.findAll();
        }

        List<Vacancy> allVacancies = vacancyRepository.findAll();
        List<Vacancy> filtered = new ArrayList<>();
        for (Vacancy vacancy : allVacancies) {
            if (!swipedVacancyIds.contains(vacancy.getId())) {
                filtered.add(vacancy);
            }
        }
        return filtered;
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

        public void registerSwipeDecision(Long userId, Long vacancyId, SwipeDecisionType decision) {
        Vacancy vacancy = vacancyRepository.findById(vacancyId)
            .orElseThrow(() -> new VacancyNotFoundException(ErrorMessages.VACANCY_NOT_FOUND));

        VacancySwipe entity = vacancySwipeRepository.findByUserIdAndVacancyId(userId, vacancyId)
            .orElseGet(VacancySwipe::new);
        entity.setUserId(userId);
        entity.setVacancyId(vacancy.getId());
        entity.setDecision(decision);
        vacancySwipeRepository.save(entity);
        }

    public List<VacancyRecommendationResponse> getRecommendedVacancies(Long userId, Float minScore, Integer limit) {
        return getRecommendedVacanciesWithProgress(userId, minScore, limit, null);
    }

    public List<VacancyRecommendationResponse> getRecommendedVacanciesWithProgress(
            Long userId,
            Float minScore,
            Integer limit,
            RecommendationProgressListener progressListener) {
        float effectiveMinScore = minScore != null ? minScore : 0f;
        int effectiveLimit = limit != null ? limit : 20;

        List<Vacancy> vacancies = vacancyRepository.findAll();
        List<VacancyRecommendationResponse> recommendations = new ArrayList<>();
        Instant cacheThreshold = Instant.now().minusSeconds(Math.max(60L, recommendationCacheTtlSeconds));
        LocalDateTime profileUpdatedAt = profileRepository.findUpdatedAtByUserId(userId)
                .orElseThrow(() -> new RuntimeException(ErrorMessages.PROFILE_NOT_FOUND));
        Set<Long> swipedVacancyIds = new HashSet<>(vacancySwipeRepository.findSwipedVacancyIdsByUserId(userId));
        int total = vacancies.size();
        int processed = 0;

        if (progressListener != null) {
            progressListener.onProgress(0, total, "Iniciando analisis de vacantes...");
        }

        for (Vacancy vacancy : vacancies) {
            try {
                if (swipedVacancyIds.contains(vacancy.getId())) {
                    continue;
                }
                LocalDateTime vacancyUpdatedAt = vacancy.getUpdatedAt() != null
                        ? vacancy.getUpdatedAt()
                        : vacancy.getCreatedAt();
                MatchingResponse match = resolveMatchWithCache(
                        userId,
                        vacancy.getId(),
                        profileUpdatedAt,
                        vacancyUpdatedAt,
                        cacheThreshold);
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
            } finally {
                processed++;
                if (progressListener != null) {
                    progressListener.onProgress(
                            processed,
                            total,
                            String.format("Procesando vacantes (%d/%d)", processed, total));
                }
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

    private MatchingResponse resolveMatchWithCache(
            Long userId,
            Long vacancyId,
            LocalDateTime profileUpdatedAt,
            LocalDateTime vacancyUpdatedAt,
            Instant cacheThreshold) {
        Optional<RecommendationCache> cached = recommendationCacheRepository.findByUserIdAndVacancyId(userId, vacancyId);
        if (cached.isPresent() && isCacheValid(cached.get(), profileUpdatedAt, vacancyUpdatedAt, cacheThreshold)) {
            return mapCacheToMatch(cached.get());
        }

        MatchingResponse freshMatch = matchingService.calculateMatch(userId, vacancyId);
        RecommendationCache entity = cached.orElseGet(RecommendationCache::new);
        entity.setUserId(userId);
        entity.setVacancyId(vacancyId);
        entity.setSimilarityScore(freshMatch.getSimilarityScore());
        entity.setCompatibilityPercentage(freshMatch.getCompatibilityPercentage());
        entity.setCompatibilityLevel(freshMatch.getCompatibilityLevel());
        entity.setFeedback(freshMatch.getFeedback());
        entity.setUsedLlmFeedback(freshMatch.getUsedLlmFeedback());
        entity.setSourceProfileUpdatedAt(profileUpdatedAt);
        entity.setSourceVacancyUpdatedAt(vacancyUpdatedAt);
        entity.setUpdatedAt(Instant.now());
        recommendationCacheRepository.save(entity);

        return freshMatch;
    }

    private MatchingResponse mapCacheToMatch(RecommendationCache cached) {
        return MatchingResponse.builder()
                .similarityScore(cached.getSimilarityScore())
                .compatibilityPercentage(cached.getCompatibilityPercentage())
                .compatibilityLevel(cached.getCompatibilityLevel())
                .feedback(cached.getFeedback())
                .usedLlmFeedback(cached.getUsedLlmFeedback())
                .build();
    }

    private boolean isCacheValid(
            RecommendationCache cached,
            LocalDateTime profileUpdatedAt,
            LocalDateTime vacancyUpdatedAt,
            Instant cacheThreshold) {
        if (cached.getUpdatedAt() == null || !cached.getUpdatedAt().isAfter(cacheThreshold)) {
            return false;
        }

        return Objects.equals(cached.getSourceProfileUpdatedAt(), profileUpdatedAt)
                && Objects.equals(cached.getSourceVacancyUpdatedAt(), vacancyUpdatedAt);
    }

    private List<String> orEmpty(List<String> list) {
        return list != null ? list : new ArrayList<>();
    }
}

