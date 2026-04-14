package ieti.jobswipe.service;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import ieti.jobswipe.dto.CompanyLikeActivityResponse;
import ieti.jobswipe.dto.CompanyVacancyPipelineResponse;
import ieti.jobswipe.dto.CreateVacancyRequest;
import ieti.jobswipe.dto.MatchingResponse;
import ieti.jobswipe.dto.VacancyApplicantResponse;
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

    public interface RecommendationProgressListener {
        void onProgress(int processed, int total, String message);

        default void onRecommendation(VacancyRecommendationResponse recommendation) {
            // Optional hook for partial result streaming.
        }
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

    @Transactional(readOnly = true)
    public List<Vacancy> getAllVacanciesForUser(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException(ErrorMessages.USER_NOT_FOUND));

        if (user.getRole() == Role.COMPANY) {
            return vacancyRepository.findAllByCompanyId(userId);
        }

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

    @Transactional(readOnly = true)
    public List<CompanyLikeActivityResponse> getCompanyLikeActivity(Long companyId, Integer limit) {
        int effectiveLimit = Math.max(1, Math.min(limit != null ? limit : 20, 100));

        List<Vacancy> companyVacancies = vacancyRepository.findAllByCompanyId(companyId);
        if (companyVacancies.isEmpty()) {
            return List.of();
        }

        Map<Long, String> vacancyTitles = new HashMap<>();
        List<Long> vacancyIds = new ArrayList<>(companyVacancies.size());
        for (Vacancy vacancy : companyVacancies) {
            vacancyIds.add(vacancy.getId());
            vacancyTitles.put(vacancy.getId(), vacancy.getTitle());
        }

        List<VacancySwipe> likes = vacancySwipeRepository.findRecentByVacancyIdsAndDecision(
                vacancyIds,
                SwipeDecisionType.LIKE,
                PageRequest.of(0, effectiveLimit));

        List<CompanyLikeActivityResponse> activity = new ArrayList<>(likes.size());
        for (VacancySwipe like : likes) {
            Long candidateId = like.getUserId();
            String candidateName = userRepository.findById(candidateId)
                    .map(User::getName)
                    .orElse("Usuario " + candidateId);

            activity.add(CompanyLikeActivityResponse.builder()
                    .vacancyId(like.getVacancyId())
                    .vacancyTitle(vacancyTitles.getOrDefault(like.getVacancyId(), "Vacante"))
                    .candidateId(candidateId)
                    .candidateName(candidateName)
                    .likedAt(like.getUpdatedAt())
                    .build());
        }

        return activity;
    }

    @Transactional(readOnly = true)
    public List<CompanyVacancyPipelineResponse> getCompanyVacancyPipeline(Long companyId) {
        List<Vacancy> companyVacancies = vacancyRepository.findAllByCompanyId(companyId);
        List<CompanyVacancyPipelineResponse> pipeline = new ArrayList<>(companyVacancies.size());

        for (Vacancy vacancy : companyVacancies) {
            int applicantsCount = (int) vacancySwipeRepository
                .countByVacancyIdAndDecision(vacancy.getId(), SwipeDecisionType.LIKE);

            pipeline.add(CompanyVacancyPipelineResponse.builder()
                    .vacancyId(vacancy.getId())
                    .vacancyTitle(vacancy.getTitle())
                    .applicantsCount(applicantsCount)
                    .build());
        }

        pipeline.sort((a, b) -> Integer.compare(b.getApplicantsCount(), a.getApplicantsCount()));
        return pipeline;
    }

    @Transactional(readOnly = true)
    public List<VacancyApplicantResponse> getApplicantsByVacancy(Long companyId, Long vacancyId, Integer limit) {
        int effectiveLimit = Math.max(1, Math.min(limit != null ? limit : 50, 200));

        Vacancy vacancy = vacancyRepository.findById(vacancyId)
                .orElseThrow(() -> new VacancyNotFoundException(ErrorMessages.VACANCY_NOT_FOUND));

        if (vacancy.getCompany() == null || !companyId.equals(vacancy.getCompany().getId())) {
            throw new VacancyNotFoundException(ErrorMessages.VACANCY_NOT_FOUND);
        }

        List<VacancySwipe> likes = vacancySwipeRepository.findByVacancyIdAndDecisionOrderByUpdatedAtDesc(
                vacancyId,
                SwipeDecisionType.LIKE,
                PageRequest.of(0, effectiveLimit));

        List<Long> candidateIds = new ArrayList<>(likes.size());
        for (VacancySwipe like : likes) {
            candidateIds.add(like.getUserId());
        }
        if (candidateIds.isEmpty()) {
            return List.of();
        }

        Map<Long, RecommendationCache> cacheByUserId = new HashMap<>();
        for (RecommendationCache cache : recommendationCacheRepository.findByVacancyIdAndUserIdIn(vacancyId, candidateIds)) {
            cacheByUserId.put(cache.getUserId(), cache);
        }

        List<VacancyApplicantResponse> applicants = new ArrayList<>(likes.size());
        for (VacancySwipe like : likes) {
            Long candidateId = like.getUserId();
            RecommendationCache cache = cacheByUserId.get(candidateId);

            String candidateName = userRepository.findById(candidateId)
                    .map(User::getName)
                    .orElse("Usuario " + candidateId);

            applicants.add(VacancyApplicantResponse.builder()
                    .candidateId(candidateId)
                    .candidateName(candidateName)
                    .compatibilityPercentage(cache != null ? cache.getCompatibilityPercentage() : null)
                    .compatibilityLevel(cache != null ? cache.getCompatibilityLevel() : null)
                    .feedback(cache != null ? cache.getFeedback() : null)
                    .appliedAt(like.getUpdatedAt())
                    .build());
        }

        return applicants;
    }

    @Transactional(readOnly = true)
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
            evaluateVacancyRecommendation(
                    vacancy,
                    userId,
                    profileUpdatedAt,
                    cacheThreshold,
                    swipedVacancyIds,
                    effectiveMinScore)
                    .ifPresent(recommendation -> {
                        recommendations.add(recommendation);
                        if (progressListener != null) {
                            progressListener.onRecommendation(recommendation);
                        }
                    });

            processed++;
            notifyProgress(progressListener, processed, total);
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

    private Optional<VacancyRecommendationResponse> evaluateVacancyRecommendation(
            Vacancy vacancy,
            Long userId,
            LocalDateTime profileUpdatedAt,
            Instant cacheThreshold,
            Set<Long> swipedVacancyIds,
            float effectiveMinScore) {
        try {
            if (swipedVacancyIds.contains(vacancy.getId())) {
                return Optional.empty();
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
            if (score < effectiveMinScore) {
                return Optional.empty();
            }

            return Optional.of(VacancyRecommendationResponse.builder()
                    .vacancyId(vacancy.getId())
                    .title(vacancy.getTitle())
                    .location(vacancy.getLocation())
                    .compatibilityPercentage(match.getCompatibilityPercentage())
                    .compatibilityLevel(match.getCompatibilityLevel())
                    .similarityScore(match.getSimilarityScore())
                    .feedback(match.getFeedback())
                    .build());
        } catch (RuntimeException ex) {
            logger.warn("Skipping vacancy {} due to matching error: {}", vacancy.getId(), ex.getMessage());
            return Optional.empty();
        }
    }

    private void notifyProgress(
            RecommendationProgressListener progressListener,
            int processed,
            int total) {
        if (progressListener == null) {
            return;
        }

        progressListener.onProgress(
                processed,
                total,
                String.format("Procesando vacantes (%d/%d)", processed, total));
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

