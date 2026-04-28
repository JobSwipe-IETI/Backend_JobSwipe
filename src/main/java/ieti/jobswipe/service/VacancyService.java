package ieti.jobswipe.service;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
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
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.annotation.Caching;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.transaction.annotation.Transactional;

import ieti.jobswipe.dto.CandidateApplicationResponse;
import ieti.jobswipe.dto.CompanyLikeActivityResponse;
import ieti.jobswipe.dto.CompanyCandidateDecisionResponse;
import ieti.jobswipe.dto.CompanyVacancyPipelineResponse;
import ieti.jobswipe.dto.CreateVacancyRequest;
import ieti.jobswipe.dto.MatchingResponse;
import ieti.jobswipe.dto.UserMatchResponse;
import ieti.jobswipe.dto.VacancyApplicantResponse;
import ieti.jobswipe.dto.VacancyRecommendationResponse;
import ieti.jobswipe.model.CompanyCandidateDecision;
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
import ieti.jobswipe.repository.CompanyCandidateDecisionRepository;
import ieti.jobswipe.repository.CandidateApplicationProjection;
import ieti.jobswipe.repository.ProfileRepository;
import ieti.jobswipe.repository.RecommendationCacheRepository;
import ieti.jobswipe.repository.UserMatchProjection;
import ieti.jobswipe.repository.UserRepository;
import ieti.jobswipe.repository.VacancyRepository;
import ieti.jobswipe.repository.VacancySwipeRepository;

@Service
public class VacancyService {

    private static final String CACHE_VACANCIES_FOR_USER = "vacanciesForUser";
    private static final String CACHE_COMPANY_ACTIVITY = "companyActivity";
    private static final String CACHE_COMPANY_PIPELINE = "companyPipeline";
    private static final String CACHE_VACANCY_APPLICANTS = "vacancyApplicants";
    private static final String CACHE_USER_MATCHES = "userMatches";

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
    private final CompanyCandidateDecisionRepository companyCandidateDecisionRepository;

    @Value("${app.recommendations.cache.ttl-seconds:1800}")
    private long recommendationCacheTtlSeconds;

    public VacancyService(VacancyRepository vacancyRepository,
            UserRepository userRepository,
            MatchingService matchingService,
            ProfileRepository profileRepository,
            RecommendationCacheRepository recommendationCacheRepository,
            VacancySwipeRepository vacancySwipeRepository,
            CompanyCandidateDecisionRepository companyCandidateDecisionRepository) {
        this.vacancyRepository = vacancyRepository;
        this.userRepository = userRepository;
        this.matchingService = matchingService;
        this.profileRepository = profileRepository;
        this.recommendationCacheRepository = recommendationCacheRepository;
        this.vacancySwipeRepository = vacancySwipeRepository;
        this.companyCandidateDecisionRepository = companyCandidateDecisionRepository;
    }

    public List<Vacancy> getAllVacancies() {
        return vacancyRepository.findAll();
    }

    @Transactional(readOnly = true)
    @Cacheable(value = CACHE_VACANCIES_FOR_USER, key = "#userId")
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

        List<Vacancy> optimized = vacancyRepository.findAllNotSwipedByUser(userId);
        if (!optimized.isEmpty()) {
            return optimized;
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
    @Cacheable(value = CACHE_COMPANY_ACTIVITY, key = "#companyId + ':' + #limit")
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

        List<CompanyCandidateDecision> decisions = companyCandidateDecisionRepository != null
            ? companyCandidateDecisionRepository.findByCompanyIdAndVacancyIdIn(companyId, vacancyIds)
            : List.of();
        Set<String> decidedPairs = new HashSet<>();
        if (decisions != null) {
            for (CompanyCandidateDecision decision : decisions) {
                decidedPairs.add(decision.getCandidateId() + ":" + decision.getVacancyId());
            }
        }

        Set<Long> candidateIds = new HashSet<>();
        for (VacancySwipe like : likes) {
            candidateIds.add(like.getUserId());
        }
        Map<Long, String> candidateNames = new HashMap<>();
        for (User candidate : userRepository.findAllById(candidateIds)) {
            candidateNames.put(candidate.getId(), candidate.getName());
        }

        List<CompanyLikeActivityResponse> activity = new ArrayList<>(likes.size());
        for (VacancySwipe like : likes) {
            Long candidateId = like.getUserId();
            String pairKey = candidateId + ":" + like.getVacancyId();
            if (decidedPairs.contains(pairKey)) {
                continue;
            }

            String candidateName = candidateNames.get(candidateId);
            if (candidateName == null) {
                candidateName = userRepository.findById(candidateId)
                        .map(User::getName)
                        .orElse("Usuario " + candidateId);
            }

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
    @Cacheable(value = CACHE_COMPANY_PIPELINE, key = "#companyId")
    public List<CompanyVacancyPipelineResponse> getCompanyVacancyPipeline(Long companyId) {
        List<Vacancy> companyVacancies = vacancyRepository.findAllByCompanyId(companyId);
        List<CompanyVacancyPipelineResponse> pipeline = new ArrayList<>(companyVacancies.size());

        if (companyVacancies.isEmpty()) {
            return List.of();
        }

        List<Long> vacancyIds = new ArrayList<>(companyVacancies.size());
        for (Vacancy vacancy : companyVacancies) {
            vacancyIds.add(vacancy.getId());
        }

        List<Object[]> groupedCounts;
        if (companyCandidateDecisionRepository != null) {
            groupedCounts = vacancySwipeRepository.countPendingLikesByVacancyIds(companyId, vacancyIds);
        } else {
            // Legacy fallback used by tests that construct the service without this repository.
            groupedCounts = vacancySwipeRepository.countByVacancyIdsAndDecision(vacancyIds, SwipeDecisionType.LIKE);
        }
        Map<Long, Integer> likesCountByVacancyId = new HashMap<>();
        for (Object[] row : groupedCounts) {
            Number vacancyIdRaw = (Number) row[0];
            Long vacancyId = vacancyIdRaw.longValue();
            Number likesCount = (Number) row[1];
            likesCountByVacancyId.put(vacancyId, likesCount.intValue());
        }

        for (Vacancy vacancy : companyVacancies) {
            int applicantsCount = likesCountByVacancyId.getOrDefault(vacancy.getId(), 0);

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
    @Cacheable(value = CACHE_VACANCY_APPLICANTS, key = "#companyId + ':' + #vacancyId + ':' + #limit")
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

        List<CompanyCandidateDecision> decisions = companyCandidateDecisionRepository != null
            ? companyCandidateDecisionRepository.findByCompanyIdAndVacancyIdAndCandidateIdIn(
                companyId,
                vacancyId,
                candidateIds)
            : List.of();
        Set<Long> decidedCandidateIds = new HashSet<>();
        if (decisions != null) {
            for (CompanyCandidateDecision decision : decisions) {
                decidedCandidateIds.add(decision.getCandidateId());
            }
        }

        Map<Long, RecommendationCache> cacheByUserId = new HashMap<>();
        for (RecommendationCache cache : recommendationCacheRepository.findByVacancyIdAndUserIdIn(vacancyId, candidateIds)) {
            cacheByUserId.put(cache.getUserId(), cache);
        }

        Map<Long, String> candidateNames = new HashMap<>();
        for (User candidate : userRepository.findAllById(candidateIds)) {
            candidateNames.put(candidate.getId(), candidate.getName());
        }

        List<VacancyApplicantResponse> applicants = new ArrayList<>(likes.size());
        for (VacancySwipe like : likes) {
            Long candidateId = like.getUserId();
            if (decidedCandidateIds.contains(candidateId)) {
                continue;
            }
            RecommendationCache cache = cacheByUserId.get(candidateId);

            String candidateName = candidateNames.get(candidateId);
            if (candidateName == null) {
                candidateName = userRepository.findById(candidateId)
                        .map(User::getName)
                        .orElse("Usuario " + candidateId);
            }

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

        @Caching(evict = {
            @CacheEvict(value = CACHE_VACANCIES_FOR_USER, allEntries = true),
            @CacheEvict(value = CACHE_COMPANY_ACTIVITY, allEntries = true),
            @CacheEvict(value = CACHE_COMPANY_PIPELINE, allEntries = true),
            @CacheEvict(value = CACHE_VACANCY_APPLICANTS, allEntries = true),
            @CacheEvict(value = CACHE_USER_MATCHES, allEntries = true)
        })
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
                .experienceLevel(parseExperienceLevel(request.getExperienceLevel()))
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

        @Caching(evict = {
            @CacheEvict(value = CACHE_VACANCIES_FOR_USER, allEntries = true),
            @CacheEvict(value = CACHE_COMPANY_ACTIVITY, allEntries = true),
            @CacheEvict(value = CACHE_COMPANY_PIPELINE, allEntries = true),
            @CacheEvict(value = CACHE_VACANCY_APPLICANTS, allEntries = true),
            @CacheEvict(value = CACHE_USER_MATCHES, allEntries = true)
        })
    public Vacancy updateVacancy(Long id, CreateVacancyRequest request) {
        Vacancy existing = vacancyRepository.findById(id)
                .orElseThrow(() -> new VacancyNotFoundException(ErrorMessages.VACANCY_NOT_FOUND));

        existing.setTitle(request.getTitle());
        existing.setDescription(request.getDescription());
        existing.setLocation(request.getLocation());
        existing.setModality(Modality.valueOf(request.getModality()));
        existing.setEmploymentType(EmploymentType.valueOf(request.getEmploymentType()));
        existing.setExperienceLevel(parseExperienceLevel(request.getExperienceLevel()));
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

        @Caching(evict = {
            @CacheEvict(value = CACHE_VACANCIES_FOR_USER, allEntries = true),
            @CacheEvict(value = CACHE_COMPANY_ACTIVITY, allEntries = true),
            @CacheEvict(value = CACHE_COMPANY_PIPELINE, allEntries = true),
            @CacheEvict(value = CACHE_VACANCY_APPLICANTS, allEntries = true),
            @CacheEvict(value = CACHE_USER_MATCHES, allEntries = true)
        })
    public void deleteVacancy(Long id) {
        Vacancy existing = vacancyRepository.findById(id)
                .orElseThrow(() -> new VacancyNotFoundException(ErrorMessages.VACANCY_NOT_FOUND));
        vacancyRepository.delete(existing);
    }

        @Caching(evict = {
            @CacheEvict(value = CACHE_VACANCIES_FOR_USER, allEntries = true),
            @CacheEvict(value = CACHE_COMPANY_ACTIVITY, allEntries = true),
            @CacheEvict(value = CACHE_COMPANY_PIPELINE, allEntries = true),
            @CacheEvict(value = CACHE_VACANCY_APPLICANTS, allEntries = true),
            @CacheEvict(value = CACHE_USER_MATCHES, allEntries = true)
        })
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

        @Caching(evict = {
            @CacheEvict(value = CACHE_COMPANY_ACTIVITY, allEntries = true),
            @CacheEvict(value = CACHE_COMPANY_PIPELINE, allEntries = true),
            @CacheEvict(value = CACHE_VACANCY_APPLICANTS, allEntries = true),
            @CacheEvict(value = CACHE_USER_MATCHES, allEntries = true)
        })
        public CompanyCandidateDecisionResponse registerCompanyCandidateDecision(
            Long companyId,
            Long vacancyId,
            Long candidateId,
            SwipeDecisionType decision,
            String rejectionReason,
            List<String> rejectionTags,
            List<String> missingTechnologies,
            List<String> missingResponsibilities,
            List<String> missingTechnicalRequirements,
            String expectedExperienceLevel,
            String aiSummary,
            String rejectionComment) {
        User company = userRepository.findById(companyId)
            .orElseThrow(() -> new RuntimeException(ErrorMessages.USER_NOT_FOUND));
        if (company.getRole() != Role.COMPANY) {
            throw new IllegalArgumentException("Only company users can decide over candidates");
        }

        User candidate = userRepository.findById(candidateId)
            .orElseThrow(() -> new RuntimeException(ErrorMessages.USER_NOT_FOUND));
        if (candidate.getRole() != Role.CANDIDATE) {
            throw new IllegalArgumentException("Only candidate users can be evaluated for vacancy matches");
        }

        Vacancy vacancy = vacancyRepository.findById(vacancyId)
            .orElseThrow(() -> new VacancyNotFoundException(ErrorMessages.VACANCY_NOT_FOUND));
        if (vacancy.getCompany() == null || !companyId.equals(vacancy.getCompany().getId())) {
            throw new VacancyNotFoundException(ErrorMessages.VACANCY_NOT_FOUND);
        }

        CompanyCandidateDecision entity = companyCandidateDecisionRepository
            .findByCompanyIdAndCandidateIdAndVacancyId(companyId, candidateId, vacancyId)
            .orElseGet(CompanyCandidateDecision::new);
        entity.setCompanyId(companyId);
        entity.setCandidateId(candidateId);
        entity.setVacancyId(vacancyId);
        entity.setDecision(decision);
        if (decision == SwipeDecisionType.DISLIKE) {
            entity.setRejectionReason(normalizeText(rejectionReason, 120));
            entity.setRejectionTags(normalizeCsv(rejectionTags, 600));
            entity.setMissingTechnologies(normalizeCsv(missingTechnologies, 600));
            entity.setMissingResponsibilities(normalizeCsv(missingResponsibilities, 1200));
            entity.setMissingTechnicalRequirements(normalizeCsv(missingTechnicalRequirements, 1200));
            entity.setExpectedExperienceLevel(normalizeText(expectedExperienceLevel, 32));

            String fallbackAiSummary = recommendationCacheRepository
                    .findByUserIdAndVacancyId(candidateId, vacancyId)
                    .map(RecommendationCache::getFeedback)
                    .orElse(null);
            entity.setAiSummary(normalizeText(
                    StringUtils.hasText(aiSummary) ? aiSummary : fallbackAiSummary,
                    5000));
            entity.setRejectionComment(normalizeText(rejectionComment, 1200));
        } else {
            entity.setRejectionReason(null);
            entity.setRejectionTags(null);
            entity.setMissingTechnologies(null);
            entity.setMissingResponsibilities(null);
            entity.setMissingTechnicalRequirements(null);
            entity.setExpectedExperienceLevel(null);
            entity.setAiSummary(null);
            entity.setRejectionComment(null);
        }
        companyCandidateDecisionRepository.save(entity);

        boolean candidateLikedVacancy = vacancySwipeRepository.existsByUserIdAndVacancyIdAndDecision(
            candidateId,
            vacancyId,
            SwipeDecisionType.LIKE);
        boolean matched = decision == SwipeDecisionType.LIKE && candidateLikedVacancy;

        return CompanyCandidateDecisionResponse.builder()
            .companyId(companyId)
            .candidateId(candidateId)
            .vacancyId(vacancyId)
            .decision(decision)
            .matched(matched)
            .rejectionReason(entity.getRejectionReason())
            .rejectionTags(splitCsv(entity.getRejectionTags()))
            .missingTechnologies(splitCsv(entity.getMissingTechnologies()))
            .missingResponsibilities(splitCsv(entity.getMissingResponsibilities()))
            .missingTechnicalRequirements(splitCsv(entity.getMissingTechnicalRequirements()))
            .expectedExperienceLevel(entity.getExpectedExperienceLevel())
            .aiSummary(entity.getAiSummary())
            .rejectionComment(entity.getRejectionComment())
            .build();
        }

        @Transactional(readOnly = true)
        public List<CandidateApplicationResponse> getCandidateApplications(Long candidateId, Integer limit) {
            User candidate = userRepository.findById(candidateId)
                    .orElseThrow(() -> new RuntimeException(ErrorMessages.USER_NOT_FOUND));
            if (candidate.getRole() != Role.CANDIDATE) {
                throw new IllegalArgumentException("Only candidate users can query applications");
            }

            int effectiveLimit = Math.max(1, Math.min(limit != null ? limit : 30, 100));
            List<CandidateApplicationProjection> rows = vacancySwipeRepository.findCandidateApplications(candidateId, effectiveLimit);
            if (rows.isEmpty()) {
                return List.of();
            }

            List<CandidateApplicationResponse> applications = new ArrayList<>(rows.size());
            for (CandidateApplicationProjection row : rows) {
                SwipeDecisionType companyDecision = null;
                if (StringUtils.hasText(row.getDecision())) {
                    companyDecision = SwipeDecisionType.valueOf(row.getDecision());
                }

                applications.add(CandidateApplicationResponse.builder()
                        .vacancyId(row.getVacancyId())
                        .vacancyTitle(row.getVacancyTitle() != null ? row.getVacancyTitle() : "Vacante")
                        .companyId(row.getCompanyId())
                        .companyName(row.getCompanyName() != null ? row.getCompanyName() : "Empresa")
                        .appliedAt(row.getAppliedAt())
                        .decision(companyDecision)
                        .decisionAt(row.getDecisionAt())
                        .matched(Boolean.TRUE.equals(row.getMatched()))
                        .rejectionReason(row.getRejectionReason())
                        .rejectionTags(splitCsv(row.getRejectionTags()))
                        .missingTechnologies(splitCsv(row.getMissingTechnologies()))
                        .missingResponsibilities(splitCsv(row.getMissingResponsibilities()))
                        .missingTechnicalRequirements(splitCsv(row.getMissingTechnicalRequirements()))
                        .expectedExperienceLevel(row.getExpectedExperienceLevel())
                        .aiSummary(row.getAiSummary())
                        .rejectionComment(row.getRejectionComment())
                        .build());
            }

            return applications;
        }

        @Transactional(readOnly = true)
        @Cacheable(value = CACHE_USER_MATCHES, key = "#userId + ':' + #limit")
        public List<UserMatchResponse> getMatchesForUser(Long userId, Integer limit) {
        User user = userRepository.findById(userId)
            .orElseThrow(() -> new RuntimeException(ErrorMessages.USER_NOT_FOUND));

        int effectiveLimit = Math.max(1, Math.min(limit != null ? limit : 20, 100));
        if (user.getRole() == Role.COMPANY) {
            return getMatchesForCompany(userId, effectiveLimit);
        }
        return getMatchesForCandidate(userId, effectiveLimit);
        }

        private List<UserMatchResponse> getMatchesForCandidate(Long candidateId, int limit) {
            return mapMatchProjections(
                    companyCandidateDecisionRepository.findCandidateMatches(candidateId, limit));
        }

        private List<UserMatchResponse> getMatchesForCompany(Long companyId, int limit) {
            return mapMatchProjections(
                    companyCandidateDecisionRepository.findCompanyMatches(companyId, limit));
        }

        private List<UserMatchResponse> mapMatchProjections(List<UserMatchProjection> rows) {
            if (rows.isEmpty()) {
                return List.of();
            }

            List<UserMatchResponse> matches = new ArrayList<>(rows.size());
            for (UserMatchProjection row : rows) {
                matches.add(UserMatchResponse.builder()
                        .vacancyId(row.getVacancyId())
                        .vacancyTitle(row.getVacancyTitle() != null ? row.getVacancyTitle() : "Vacante")
                        .counterpartId(row.getCounterpartId())
                        .counterpartName(row.getCounterpartName() != null ? row.getCounterpartName() : "Usuario")
                        .matchedAt(row.getMatchedAt())
                        .compatibilityPercentage(row.getCompatibilityPercentage())
                        .compatibilityLevel(row.getCompatibilityLevel())
                        .build());
            }
            return matches;
        }

        private String normalizeText(String value, int maxLen) {
            if (!StringUtils.hasText(value)) {
                return null;
            }
            String trimmed = value.trim();
            if (trimmed.length() > maxLen) {
                return trimmed.substring(0, maxLen);
            }
            return trimmed;
        }

        private String normalizeCsv(List<String> values, int maxLen) {
            if (values == null || values.isEmpty()) {
                return null;
            }

            List<String> cleaned = values.stream()
                    .filter(StringUtils::hasText)
                    .map(String::trim)
                    .filter(item -> !item.isEmpty())
                    .distinct()
                    .toList();
            if (cleaned.isEmpty()) {
                return null;
            }

            String joined = String.join(",", cleaned);
            if (joined.length() > maxLen) {
                return joined.substring(0, maxLen);
            }
            return joined;
        }

        private List<String> splitCsv(String value) {
            if (!StringUtils.hasText(value)) {
                return List.of();
            }
            return Arrays.stream(value.split(","))
                    .map(String::trim)
                    .filter(item -> !item.isEmpty())
                    .toList();
        }

        private ExperienceLevel parseExperienceLevel(String rawValue) {
            if (!StringUtils.hasText(rawValue)) {
                throw new IllegalArgumentException("Experience level is required");
            }

            String normalized = rawValue.trim().toUpperCase();
            if ("MID".equals(normalized)) {
                return ExperienceLevel.MID;
            }
            if ("SEMI_SENIOR".equals(normalized) || "SEMI-SENIOR".equals(normalized)) {
                return ExperienceLevel.SEMI_SENIOR;
            }

            return ExperienceLevel.valueOf(normalized);
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

        Set<Long> swipedVacancyIds = new HashSet<>(vacancySwipeRepository.findSwipedVacancyIdsByUserId(userId));
        List<Vacancy> vacancies;
        if (swipedVacancyIds.isEmpty()) {
            vacancies = vacancyRepository.findAll();
        } else {
            vacancies = vacancyRepository.findAllNotSwipedByUser(userId);
            if (vacancies.isEmpty()) {
                List<Vacancy> allVacancies = vacancyRepository.findAll();
                List<Vacancy> filtered = new ArrayList<>();
                for (Vacancy vacancy : allVacancies) {
                    if (!swipedVacancyIds.contains(vacancy.getId())) {
                        filtered.add(vacancy);
                    }
                }
                vacancies = filtered;
            }
        }
        List<VacancyRecommendationResponse> recommendations = new ArrayList<>();
        Instant cacheThreshold = Instant.now().minusSeconds(Math.max(60L, recommendationCacheTtlSeconds));
        LocalDateTime profileUpdatedAt = profileRepository.findUpdatedAtByUserId(userId)
                .orElseThrow(() -> new RuntimeException(ErrorMessages.PROFILE_NOT_FOUND));
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

