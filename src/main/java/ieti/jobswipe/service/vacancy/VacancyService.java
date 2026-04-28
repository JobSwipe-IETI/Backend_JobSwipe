package ieti.jobswipe.service.vacancy;

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
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.annotation.Caching;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import ieti.jobswipe.dto.chat.ChatRealtimeEventResponse;
import ieti.jobswipe.dto.company.CompanyCandidateDecisionResponse;
import ieti.jobswipe.dto.company.CompanyCandidateDecisionRequest;
import ieti.jobswipe.dto.company.CompanyLikeActivityResponse;
import ieti.jobswipe.dto.company.CompanyVacancyPipelineResponse;
import ieti.jobswipe.dto.matching.MatchingResponse;
import ieti.jobswipe.dto.matching.UserMatchResponse;
import ieti.jobswipe.dto.user.CandidateApplicationResponse;
import ieti.jobswipe.dto.vacancy.CreateVacancyRequest;
import ieti.jobswipe.dto.vacancy.VacancyApplicantResponse;
import ieti.jobswipe.dto.vacancy.VacancyDetailResponse;
import ieti.jobswipe.dto.vacancy.VacancyRecommendationResponse;
import ieti.jobswipe.dto.vacancy.VacancySummaryResponse;
import ieti.jobswipe.exception.ErrorMessages;
import ieti.jobswipe.exception.VacancyNotFoundException;
import ieti.jobswipe.model.EmploymentType;
import ieti.jobswipe.model.ExperienceLevel;
import ieti.jobswipe.model.Modality;
import ieti.jobswipe.model.Role;
import ieti.jobswipe.model.SwipeDecisionType;
import ieti.jobswipe.model.entity.CompanyCandidateDecision;
import ieti.jobswipe.model.entity.RecommendationCache;
import ieti.jobswipe.model.entity.User;
import ieti.jobswipe.model.entity.Vacancy;
import ieti.jobswipe.model.entity.VacancySwipe;
import ieti.jobswipe.repository.company.CompanyCandidateDecisionRepository;
import ieti.jobswipe.repository.profile.ProfileRepository;
import ieti.jobswipe.repository.projection.CandidateApplicationProjection;
import ieti.jobswipe.repository.projection.UserMatchProjection;
import ieti.jobswipe.repository.recommendation.RecommendationCacheRepository;
import ieti.jobswipe.repository.user.UserRepository;
import ieti.jobswipe.repository.vacancy.VacancyRepository;
import ieti.jobswipe.repository.vacancy.VacancySwipeRepository;
import ieti.jobswipe.service.chat.ChatRealtimeService;
import ieti.jobswipe.service.matching.MatchingService;

@Service
public class VacancyService {
    private static final String VACANTE = "Vacante";
    private static final String ACTOR_USER_ID = "actorUserId";
    private static final String VACANCY_ID = "vacancyId";
    private static final String VACANCY_TITLE = "vacancyTitle";
    private static final String COUNTERPART_ID = "counterpartId";
    private static final String COUNTERPART_NAME = "counterpartName";
    private static final String NOTIFICATION_MATCH = "notification.match";

    private static final String CACHE_VACANCIES_FOR_USER = "vacanciesForUser";
    private static final String CACHE_COMPANY_ACTIVITY = "companyActivity";
    private static final String CACHE_COMPANY_PIPELINE = "companyPipeline";
    private static final String CACHE_VACANCY_APPLICANTS = "vacancyApplicants";
    private static final String CACHE_USER_MATCHES = "userMatches";
    private static final String CACHE_CANDIDATE_APPLICATIONS = "candidateApplications";
    private static final String CACHE_VACANCY_SUMMARIES_FOR_USER = "vacancySummariesForUser";

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
    private final CacheManager cacheManager;
    private ChatRealtimeService chatRealtimeService;

    @Value("${app.recommendations.cache.ttl-seconds:1800}")
    private long recommendationCacheTtlSeconds;

    public VacancyService(VacancyRepository vacancyRepository,
            UserRepository userRepository,
            MatchingService matchingService,
            ProfileRepository profileRepository,
            RecommendationCacheRepository recommendationCacheRepository,
            VacancySwipeRepository vacancySwipeRepository,
            CompanyCandidateDecisionRepository companyCandidateDecisionRepository,
            CacheManager cacheManager) {
        this.vacancyRepository = vacancyRepository;
        this.userRepository = userRepository;
        this.matchingService = matchingService;
        this.profileRepository = profileRepository;
        this.recommendationCacheRepository = recommendationCacheRepository;
        this.vacancySwipeRepository = vacancySwipeRepository;
        this.companyCandidateDecisionRepository = companyCandidateDecisionRepository;
        this.cacheManager = cacheManager;
    }

    @Autowired(required = false)
    public void setChatRealtimeService(ChatRealtimeService chatRealtimeService) {
        this.chatRealtimeService = chatRealtimeService;
    }

    public List<Vacancy> getAllVacancies() {
        return vacancyRepository.findAll();
    }

    @Transactional(readOnly = true)
    @Cacheable(value = CACHE_VACANCIES_FOR_USER, key = "#userId", sync = true)
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
    public List<VacancySummaryResponse> getVacancySummariesForUser(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException(ErrorMessages.USER_NOT_FOUND));

        if (user.getRole() == Role.COMPANY) {
            return vacancyRepository.findSummaryByCompanyId(userId);
        }

        Set<Long> swipedVacancyIds = new HashSet<>(vacancySwipeRepository.findSwipedVacancyIdsByUserId(userId));
        if (swipedVacancyIds.isEmpty()) {
            return vacancyRepository.findAllSummaries();
        }

        List<VacancySummaryResponse> optimized = vacancyRepository.findSummaryNotSwipedByUser(userId);
        if (!optimized.isEmpty()) {
            return optimized;
        }

        return vacancyRepository.findAllSummaries().stream()
                .filter(vacancy -> !swipedVacancyIds.contains(vacancy.id()))
                .toList();
    }

    @Transactional(readOnly = true)
    @Cacheable(value = CACHE_COMPANY_ACTIVITY, key = "#companyId + ':' + #limit", sync = true)
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
                candidateName = "Usuario " + candidateId;
            }

            activity.add(CompanyLikeActivityResponse.builder()
                    .vacancyId(like.getVacancyId())
                    .vacancyTitle(vacancyTitles.getOrDefault(like.getVacancyId(), VACANTE))
                    .candidateId(candidateId)
                    .candidateName(candidateName)
                    .likedAt(like.getUpdatedAt())
                    .build());
        }

        return activity;
    }

    @Transactional(readOnly = true)
    @Cacheable(value = CACHE_COMPANY_PIPELINE, key = "#companyId", sync = true)
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
    @Cacheable(value = CACHE_VACANCY_APPLICANTS, key = "#companyId + ':' + #vacancyId + ':' + #limit", sync = true)
    public List<VacancyApplicantResponse> getApplicantsByVacancy(Long companyId, Long vacancyId, Integer limit) {
        int effectiveLimit = Math.max(1, Math.min(limit != null ? limit : 50, 200));

        getCompanyVacancyOrThrow(companyId, vacancyId);

        List<VacancySwipe> likes = vacancySwipeRepository.findByVacancyIdAndDecisionOrderByUpdatedAtDesc(
                vacancyId,
                SwipeDecisionType.LIKE,
                PageRequest.of(0, effectiveLimit));

        List<Long> candidateIds = extractCandidateIds(likes);
        if (candidateIds.isEmpty()) {
            return List.of();
        }

        Set<Long> decidedCandidateIds = findDecidedCandidateIds(companyId, vacancyId, candidateIds);
        Map<Long, RecommendationCache> cacheByUserId = findRecommendationCacheByUserId(vacancyId, candidateIds);
        Map<Long, String> candidateNames = findCandidateNamesById(candidateIds);
        return buildApplicantResponses(likes, decidedCandidateIds, cacheByUserId, candidateNames);
    }

    @Transactional(readOnly = true)
    public VacancyDetailResponse getVacancyById(Long id) {
        return mapVacancyDetail(
                vacancyRepository.findById(id)
                        .orElseThrow(() -> new VacancyNotFoundException(ErrorMessages.VACANCY_NOT_FOUND)));
    }

        @Caching(evict = {
            @CacheEvict(value = CACHE_VACANCIES_FOR_USER, allEntries = true),
            @CacheEvict(value = CACHE_VACANCY_SUMMARIES_FOR_USER, allEntries = true),
            @CacheEvict(value = CACHE_COMPANY_ACTIVITY, allEntries = true),
            @CacheEvict(value = CACHE_COMPANY_PIPELINE, allEntries = true),
            @CacheEvict(value = CACHE_VACANCY_APPLICANTS, allEntries = true),
            @CacheEvict(value = CACHE_USER_MATCHES, allEntries = true),
            @CacheEvict(value = CACHE_CANDIDATE_APPLICATIONS, allEntries = true)
        })
    public VacancyDetailResponse createVacancy(CreateVacancyRequest request, Long companyId) {
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

        return mapVacancyDetail(vacancyRepository.save(vacancy));
    }

        @Caching(evict = {
            @CacheEvict(value = CACHE_VACANCIES_FOR_USER, allEntries = true),
            @CacheEvict(value = CACHE_VACANCY_SUMMARIES_FOR_USER, allEntries = true),
            @CacheEvict(value = CACHE_COMPANY_ACTIVITY, allEntries = true),
            @CacheEvict(value = CACHE_COMPANY_PIPELINE, allEntries = true),
            @CacheEvict(value = CACHE_VACANCY_APPLICANTS, allEntries = true),
            @CacheEvict(value = CACHE_USER_MATCHES, allEntries = true),
            @CacheEvict(value = CACHE_CANDIDATE_APPLICATIONS, allEntries = true)
        })
    public VacancyDetailResponse updateVacancy(Long id, CreateVacancyRequest request) {
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

        return mapVacancyDetail(vacancyRepository.save(existing));
    }

        @Caching(evict = {
            @CacheEvict(value = CACHE_VACANCIES_FOR_USER, allEntries = true),
            @CacheEvict(value = CACHE_VACANCY_SUMMARIES_FOR_USER, allEntries = true),
            @CacheEvict(value = CACHE_COMPANY_ACTIVITY, allEntries = true),
            @CacheEvict(value = CACHE_COMPANY_PIPELINE, allEntries = true),
            @CacheEvict(value = CACHE_VACANCY_APPLICANTS, allEntries = true),
            @CacheEvict(value = CACHE_USER_MATCHES, allEntries = true),
            @CacheEvict(value = CACHE_CANDIDATE_APPLICATIONS, allEntries = true)
        })
    public void deleteVacancy(Long id) {
        Vacancy existing = vacancyRepository.findById(id)
                .orElseThrow(() -> new VacancyNotFoundException(ErrorMessages.VACANCY_NOT_FOUND));

        // Borrar relaciones asociadas
        vacancySwipeRepository.deleteByVacancyId(id);
        companyCandidateDecisionRepository.deleteByVacancyId(id);
        recommendationCacheRepository.deleteByVacancyId(id);

        vacancyRepository.delete(existing);
    }

        public void registerSwipeDecision(Long userId, Long vacancyId, SwipeDecisionType decision) {
        Vacancy vacancy = vacancyRepository.findById(vacancyId)
            .orElseThrow(() -> new VacancyNotFoundException(ErrorMessages.VACANCY_NOT_FOUND));

        saveSwipeDecision(userId, vacancy, decision);
        invalidateSwipeCaches(userId, vacancy);

        if (decision != SwipeDecisionType.LIKE || vacancy.getCompany() == null) {
            return;
        }

        notifyCompanyLike(userId, vacancyId, vacancy);
        }

        public CompanyCandidateDecisionResponse registerCompanyCandidateDecision(
            Long companyId,
            Long vacancyId,
            Long candidateId,
            CompanyCandidateDecisionRequest request) {
        SwipeDecisionType decision = request.getDecision();
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
            entity.setRejectionReason(normalizeText(request.getRejectionReason(), 120));
            entity.setRejectionTags(normalizeCsv(request.getRejectionTags(), 600));
            entity.setMissingTechnologies(normalizeCsv(request.getMissingTechnologies(), 600));
            entity.setMissingResponsibilities(normalizeCsv(request.getMissingResponsibilities(), 1200));
            entity.setMissingTechnicalRequirements(normalizeCsv(request.getMissingTechnicalRequirements(), 1200));
            entity.setExpectedExperienceLevel(normalizeText(request.getExpectedExperienceLevel(), 32));

            String fallbackAiSummary = recommendationCacheRepository
                    .findByUserIdAndVacancyId(candidateId, vacancyId)
                    .map(RecommendationCache::getFeedback)
                    .orElse(null);
            entity.setAiSummary(normalizeText(
                    StringUtils.hasText(request.getAiSummary()) ? request.getAiSummary() : fallbackAiSummary,
                    5000));
            entity.setRejectionComment(normalizeText(request.getRejectionComment(), 1200));
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
        invalidateCompanyDecisionCaches(companyId, candidateId, vacancyId);

        String vacancyTitle = StringUtils.hasText(vacancy.getTitle()) ? vacancy.getTitle() : VACANTE;
        String companyName = StringUtils.hasText(company.getName()) ? company.getName() : "Empresa";
        String candidateName = StringUtils.hasText(candidate.getName()) ? candidate.getName() : "Candidato";

        Map<String, Object> candidateDecisionPayload = new HashMap<>();
        candidateDecisionPayload.put(ACTOR_USER_ID, companyId);
        candidateDecisionPayload.put(VACANCY_ID, vacancyId);
        candidateDecisionPayload.put(VACANCY_TITLE, vacancyTitle);
        candidateDecisionPayload.put("companyId", companyId);
        candidateDecisionPayload.put("companyName", companyName);
        candidateDecisionPayload.put("decision", decision.name());
        candidateDecisionPayload.put("matched", matched);
        publishRealtimeNotification(candidateId, "notification.company_decision", candidateDecisionPayload);

        if (matched) {
            Map<String, Object> candidateMatchPayload = new HashMap<>();
            candidateMatchPayload.put(ACTOR_USER_ID, companyId);
            candidateMatchPayload.put(VACANCY_ID, vacancyId);
            candidateMatchPayload.put(VACANCY_TITLE, vacancyTitle);
            candidateMatchPayload.put(COUNTERPART_ID, companyId);
            candidateMatchPayload.put(COUNTERPART_NAME, companyName);

            Map<String, Object> companyMatchPayload = new HashMap<>();
            companyMatchPayload.put(ACTOR_USER_ID, companyId);
            companyMatchPayload.put(VACANCY_ID, vacancyId);
            companyMatchPayload.put(VACANCY_TITLE, vacancyTitle);
            companyMatchPayload.put(COUNTERPART_ID, candidateId);
            companyMatchPayload.put(COUNTERPART_NAME, candidateName);

            publishRealtimeNotification(candidateId, NOTIFICATION_MATCH, candidateMatchPayload);
            publishRealtimeNotification(companyId, NOTIFICATION_MATCH, companyMatchPayload);
        }

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

        private void saveSwipeDecision(Long userId, Vacancy vacancy, SwipeDecisionType decision) {
        VacancySwipe entity = vacancySwipeRepository.findByUserIdAndVacancyId(userId, vacancy.getId())
            .orElseGet(VacancySwipe::new);
        entity.setUserId(userId);
        entity.setVacancyId(vacancy.getId());
        entity.setDecision(decision);
        vacancySwipeRepository.save(entity);
        }

        private void notifyCompanyLike(Long userId, Long vacancyId, Vacancy vacancy) {
        Long companyId = vacancy.getCompany().getId();
        String vacancyTitle = getVacancyTitleOrDefault(vacancy);
        String candidateName = getCandidateNameOrDefault(userId);
        boolean matched = hasCompanyLikedCandidate(companyId, userId, vacancyId);

        publishRealtimeNotification(
            companyId,
            "notification.company_like",
            buildCompanyLikePayload(userId, vacancyId, vacancyTitle, candidateName, matched));

        if (!matched) {
            return;
        }

        publishMatchNotifications(userId, companyId, vacancyId, vacancyTitle, vacancy.getCompany().getName(), candidateName);
        }

        private String getVacancyTitleOrDefault(Vacancy vacancy) {
        return StringUtils.hasText(vacancy.getTitle()) ? vacancy.getTitle() : VACANTE;
        }

        private String getCandidateNameOrDefault(Long candidateId) {
        User candidate = userRepository.findById(candidateId).orElse(null);
        return candidate != null && StringUtils.hasText(candidate.getName())
            ? candidate.getName()
            : "Candidato";
        }

        private boolean hasCompanyLikedCandidate(Long companyId, Long candidateId, Long vacancyId) {
        return companyCandidateDecisionRepository.existsByCompanyIdAndCandidateIdAndVacancyIdAndDecision(
            companyId,
            candidateId,
            vacancyId,
            SwipeDecisionType.LIKE);
        }

        private Map<String, Object> buildCompanyLikePayload(
            Long userId,
            Long vacancyId,
            String vacancyTitle,
            String candidateName,
            boolean matched) {
        Map<String, Object> companyLikePayload = new HashMap<>();
        companyLikePayload.put(ACTOR_USER_ID, userId);
        companyLikePayload.put(VACANCY_ID, vacancyId);
        companyLikePayload.put(VACANCY_TITLE, vacancyTitle);
        companyLikePayload.put("candidateId", userId);
        companyLikePayload.put("candidateName", candidateName);
        companyLikePayload.put("matched", matched);
        return companyLikePayload;
        }

        private void publishMatchNotifications(
            Long actorUserId,
            Long companyId,
            Long vacancyId,
            String vacancyTitle,
            String companyName,
            String candidateName) {
        Map<String, Object> candidateMatchPayload = new HashMap<>();
        candidateMatchPayload.put(ACTOR_USER_ID, actorUserId);
        candidateMatchPayload.put(VACANCY_ID, vacancyId);
        candidateMatchPayload.put(VACANCY_TITLE, vacancyTitle);
        candidateMatchPayload.put(COUNTERPART_ID, companyId);
        candidateMatchPayload.put(COUNTERPART_NAME, companyName);

        Map<String, Object> companyMatchPayload = new HashMap<>();
        companyMatchPayload.put(ACTOR_USER_ID, actorUserId);
        companyMatchPayload.put(VACANCY_ID, vacancyId);
        companyMatchPayload.put(VACANCY_TITLE, vacancyTitle);
        companyMatchPayload.put(COUNTERPART_ID, actorUserId);
        companyMatchPayload.put(COUNTERPART_NAME, candidateName);

        publishRealtimeNotification(actorUserId, NOTIFICATION_MATCH, candidateMatchPayload);
        publishRealtimeNotification(companyId, NOTIFICATION_MATCH, companyMatchPayload);
        }

        @Transactional(readOnly = true)
        @Cacheable(value = CACHE_CANDIDATE_APPLICATIONS, key = "#candidateId + ':' + #limit", sync = true)
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
                        .vacancyTitle(row.getVacancyTitle() != null ? row.getVacancyTitle() : VACANTE)
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
        @Cacheable(value = CACHE_USER_MATCHES, key = "#userId + ':' + #limit", sync = true)
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
                        .vacancyTitle(row.getVacancyTitle() != null ? row.getVacancyTitle() : VACANTE)
                        .counterpartId(row.getCounterpartId())
                        .counterpartName(row.getCounterpartName() != null ? row.getCounterpartName() : "Usuario")
                        .matchedAt(row.getMatchedAt())
                        .compatibilityPercentage(row.getCompatibilityPercentage())
                        .compatibilityLevel(row.getCompatibilityLevel())
                        .build());
            }
            return matches;
        }

        private void invalidateSwipeCaches(Long candidateId, Vacancy vacancy) {
            evictUserScopedVacancyCaches(candidateId);
            evictCandidateScopedCaches(candidateId);

            if (vacancy.getCompany() == null || vacancy.getCompany().getId() == null) {
                return;
            }

            Long companyId = vacancy.getCompany().getId();
            Long vacancyId = vacancy.getId();
            evictCompanyActivity(companyId);
            evictCompanyPipeline(companyId);
            evictVacancyApplicants(companyId, vacancyId);
            evictUserMatches(companyId);
        }

        private void invalidateCompanyDecisionCaches(Long companyId, Long candidateId, Long vacancyId) {
            evictCompanyActivity(companyId);
            evictCompanyPipeline(companyId);
            evictVacancyApplicants(companyId, vacancyId);
            evictUserMatches(companyId);

            evictCandidateScopedCaches(candidateId);
            evictUserMatches(candidateId);
        }

        private void evictUserScopedVacancyCaches(Long userId) {
            evictCacheKey(CACHE_VACANCIES_FOR_USER, userId);
            evictCacheKey(CACHE_VACANCY_SUMMARIES_FOR_USER, userId);
        }

        private void evictCandidateScopedCaches(Long candidateId) {
            for (int limit : new int[] { 30, 50, 100 }) {
                evictCacheKey(CACHE_CANDIDATE_APPLICATIONS, candidateId + ":" + limit);
            }
        }

        private void evictCompanyActivity(Long companyId) {
            for (int limit : new int[] { 20, 50, 100 }) {
                evictCacheKey(CACHE_COMPANY_ACTIVITY, companyId + ":" + limit);
            }
        }

        private void evictCompanyPipeline(Long companyId) {
            evictCacheKey(CACHE_COMPANY_PIPELINE, companyId);
        }

        private void evictVacancyApplicants(Long companyId, Long vacancyId) {
            for (int limit : new int[] { 20, 50, 100, 200 }) {
                evictCacheKey(CACHE_VACANCY_APPLICANTS, companyId + ":" + vacancyId + ":" + limit);
            }
        }

        private void evictUserMatches(Long userId) {
            for (int limit : new int[] { 20, 30, 50, 100 }) {
                evictCacheKey(CACHE_USER_MATCHES, userId + ":" + limit);
            }
        }

        private void evictCacheKey(String cacheName, Object key) {
            if (cacheManager == null || key == null) {
                return;
            }

            Cache cache = cacheManager.getCache(cacheName);
            if (cache != null) {
                cache.evict(key);
            }
        }

        private void publishRealtimeNotification(Long userId, String type, Map<String, Object> payload) {
            if (chatRealtimeService == null || userId == null || !StringUtils.hasText(type)) {
                return;
            }

            chatRealtimeService.publishToUser(
                    userId,
                    ChatRealtimeEventResponse.builder()
                            .type(type)
                            .payload(payload)
                            .build());
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

        private VacancyDetailResponse mapVacancyDetail(Vacancy vacancy) {
            return new VacancyDetailResponse(
                    vacancy.getId(),
                    vacancy.getTitle(),
                    vacancy.getDescription(),
                    vacancy.getLocation(),
                    vacancy.getSector(),
                    vacancy.getModality() != null ? vacancy.getModality().name() : null,
                    vacancy.getEmploymentType() != null ? vacancy.getEmploymentType().name() : null,
                    vacancy.getExperienceLevel() != null ? vacancy.getExperienceLevel().name() : null,
                    vacancy.getTechnologies() != null ? List.copyOf(vacancy.getTechnologies()) : List.of(),
                    vacancy.getSoftSkills() != null ? List.copyOf(vacancy.getSoftSkills()) : List.of(),
                    vacancy.getResponsibilities() != null ? List.copyOf(vacancy.getResponsibilities()) : List.of(),
                    vacancy.getTechnicalRequirements() != null ? List.copyOf(vacancy.getTechnicalRequirements()) : List.of(),
                    vacancy.getMinSalary(),
                    vacancy.getMaxSalary(),
                    vacancy.getBenefits() != null ? List.copyOf(vacancy.getBenefits()) : List.of(),
                    vacancy.getCompany() != null ? vacancy.getCompany().getId() : null,
                    vacancy.getCompany() != null ? vacancy.getCompany().getName() : null);
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
        List<Vacancy> vacancies = findAvailableVacanciesForUser(userId, swipedVacancyIds);
        List<VacancyRecommendationResponse> recommendations = new ArrayList<>();
        Instant cacheThreshold = Instant.now().minusSeconds(Math.max(60L, recommendationCacheTtlSeconds));
        LocalDateTime profileUpdatedAt = profileRepository.findUpdatedAtByUserId(userId)
                .orElseThrow(() -> new RuntimeException(ErrorMessages.PROFILE_NOT_FOUND));
        int total = vacancies.size();
        int processed = 0;

        startProgress(progressListener, total);

        for (Vacancy vacancy : vacancies) {
            Optional<VacancyRecommendationResponse> recommendation = evaluateVacancyRecommendation(
                    vacancy,
                    userId,
                    profileUpdatedAt,
                    cacheThreshold,
                    swipedVacancyIds,
                    effectiveMinScore);
            addRecommendation(recommendations, progressListener, recommendation);

            processed++;
            notifyProgress(progressListener, processed, total);
        }

        return sortAndLimitRecommendations(recommendations, effectiveLimit);
    }

    private List<Long> extractCandidateIds(List<VacancySwipe> likes) {
        List<Long> candidateIds = new ArrayList<>(likes.size());
        for (VacancySwipe like : likes) {
            candidateIds.add(like.getUserId());
        }
        return candidateIds;
    }

    private Set<Long> findDecidedCandidateIds(Long companyId, Long vacancyId, List<Long> candidateIds) {
        List<CompanyCandidateDecision> decisions = companyCandidateDecisionRepository != null
            ? companyCandidateDecisionRepository.findByCompanyIdAndVacancyIdAndCandidateIdIn(
                companyId,
                vacancyId,
                candidateIds)
            : List.of();
        Set<Long> decidedCandidateIds = new HashSet<>();
        if (decisions == null) {
            return decidedCandidateIds;
        }
        for (CompanyCandidateDecision decision : decisions) {
            decidedCandidateIds.add(decision.getCandidateId());
        }
        return decidedCandidateIds;
    }

    private Map<Long, RecommendationCache> findRecommendationCacheByUserId(Long vacancyId, List<Long> candidateIds) {
        Map<Long, RecommendationCache> cacheByUserId = new HashMap<>();
        for (RecommendationCache cache : recommendationCacheRepository.findByVacancyIdAndUserIdIn(vacancyId, candidateIds)) {
            cacheByUserId.put(cache.getUserId(), cache);
        }
        return cacheByUserId;
    }

    private Map<Long, String> findCandidateNamesById(List<Long> candidateIds) {
        Map<Long, String> candidateNames = new HashMap<>();
        for (User candidate : userRepository.findAllById(candidateIds)) {
            candidateNames.put(candidate.getId(), candidate.getName());
        }
        return candidateNames;
    }

    private List<VacancyApplicantResponse> buildApplicantResponses(
            List<VacancySwipe> likes,
            Set<Long> decidedCandidateIds,
            Map<Long, RecommendationCache> cacheByUserId,
            Map<Long, String> candidateNames) {
        List<VacancyApplicantResponse> applicants = new ArrayList<>(likes.size());
        for (VacancySwipe like : likes) {
            Long candidateId = like.getUserId();
            if (decidedCandidateIds.contains(candidateId)) {
                continue;
            }

            RecommendationCache cache = cacheByUserId.get(candidateId);
            String candidateName = candidateNames.get(candidateId);
            if (candidateName == null) {
                candidateName = "Usuario " + candidateId;
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

    private List<Vacancy> findAvailableVacanciesForUser(Long userId, Set<Long> swipedVacancyIds) {
        if (swipedVacancyIds.isEmpty()) {
            return vacancyRepository.findAll();
        }

        List<Vacancy> vacancies = vacancyRepository.findAllNotSwipedByUser(userId);
        if (!vacancies.isEmpty()) {
            return vacancies;
        }

        List<Vacancy> filtered = new ArrayList<>();
        for (Vacancy vacancy : vacancyRepository.findAll()) {
            if (!swipedVacancyIds.contains(vacancy.getId())) {
                filtered.add(vacancy);
            }
        }
        return filtered;
    }

    private void startProgress(RecommendationProgressListener progressListener, int total) {
        if (progressListener != null) {
            progressListener.onProgress(0, total, "Iniciando analisis de vacantes...");
        }
    }

    private void addRecommendation(
            List<VacancyRecommendationResponse> recommendations,
            RecommendationProgressListener progressListener,
            Optional<VacancyRecommendationResponse> recommendation) {
        recommendation.ifPresent(foundRecommendation -> {
            recommendations.add(foundRecommendation);
            if (progressListener != null) {
                progressListener.onRecommendation(foundRecommendation);
            }
        });
    }

    private List<VacancyRecommendationResponse> sortAndLimitRecommendations(
            List<VacancyRecommendationResponse> recommendations,
            int effectiveLimit) {
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

    private Vacancy getCompanyVacancyOrThrow(Long companyId, Long vacancyId) {
        Vacancy vacancy = vacancyRepository.findById(vacancyId)
                .orElseThrow(() -> new VacancyNotFoundException(ErrorMessages.VACANCY_NOT_FOUND));
        if (vacancy.getCompany() == null || !companyId.equals(vacancy.getCompany().getId())) {
            throw new VacancyNotFoundException(ErrorMessages.VACANCY_NOT_FOUND);
        }
        return vacancy;
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

