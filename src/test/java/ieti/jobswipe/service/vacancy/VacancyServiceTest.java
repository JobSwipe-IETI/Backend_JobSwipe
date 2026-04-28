package ieti.jobswipe.service.vacancy;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.test.util.ReflectionTestUtils;

import ieti.jobswipe.dto.company.CompanyCandidateDecisionResponse;
import ieti.jobswipe.dto.company.CompanyCandidateDecisionRequest;
import ieti.jobswipe.dto.matching.MatchingResponse;
import ieti.jobswipe.dto.matching.UserMatchResponse;
import ieti.jobswipe.dto.user.CandidateApplicationResponse;
import ieti.jobswipe.dto.vacancy.CreateVacancyRequest;
import ieti.jobswipe.dto.vacancy.VacancyDetailResponse;
import ieti.jobswipe.dto.vacancy.VacancyRecommendationResponse;
import ieti.jobswipe.dto.vacancy.VacancySummaryResponse;
import ieti.jobswipe.exception.ErrorMessages;
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

@ExtendWith(MockitoExtension.class)
class VacancyServiceTest {
            @Test
            void shouldUseVacanteAsFallbackInRegisterCompanyCandidateDecision() {
                User company = User.builder().id(100L).name("EmpresaX").role(Role.COMPANY).build();
                User candidate = User.builder().id(200L).name("CandidatoY").role(Role.CANDIDATE).build();
                Vacancy vacancy = Vacancy.builder().id(300L).title(null).company(company).build();

                when(userRepository.findById(100L)).thenReturn(Optional.of(company));
                when(userRepository.findById(200L)).thenReturn(Optional.of(candidate));
                when(vacancyRepository.findById(300L)).thenReturn(Optional.of(vacancy));
                when(companyCandidateDecisionRepository.findByCompanyIdAndCandidateIdAndVacancyId(100L, 200L, 300L)).thenReturn(Optional.empty());
                when(vacancySwipeRepository.existsByUserIdAndVacancyIdAndDecision(200L, 300L, SwipeDecisionType.LIKE)).thenReturn(false);
                when(recommendationCacheRepository.findByUserIdAndVacancyId(200L, 300L)).thenReturn(Optional.empty());

                ReflectionTestUtils.setField(vacancyService, "chatRealtimeService", chatRealtimeService);

                CompanyCandidateDecisionResponse response = registerCompanyCandidateDecision(
                    100L, 300L, 200L, SwipeDecisionType.DISLIKE, "Motivo", List.of("tag"), List.of("java"), List.of("resp"), List.of("req"), "SENIOR", "summary", "comment");

                // Verifica que el fallback se use en la notificación
                verify(chatRealtimeService, atLeastOnce()).publishToUser(eq(200L), argThat(event -> {
                    Object payload = event.getPayload();
                    if (payload instanceof java.util.Map<?, ?> map) {
                        Object title = map.get("vacancyTitle");
                        return "Vacante".equals(title);
                    }
                    return false;
                }));
                // Y en la respuesta (si aplica)
                assertEquals(300L, response.getVacancyId());
            }

            @Test
            void shouldCallEvictCacheKeyViaPublicMethodWithNullCacheManager() {
                // Creamos un VacancyService con cacheManager null
                VacancyService nullCacheManagerService = new VacancyService(
                    vacancyRepository,
                    userRepository,
                    matchingService,
                    profileRepository,
                    recommendationCacheRepository,
                    vacancySwipeRepository,
                    companyCandidateDecisionRepository,
                    null);

                // Llama a un método público que internamente llama a evictCacheKey
                // getAllVacancies no lo hace, pero podrías tener un método que sí, por ejemplo registerSwipeDecision
                // Simulamos un flujo mínimo para forzar la llamada
                User candidate = User.builder().id(1L).name("Candidato").role(Role.CANDIDATE).build();
                User company = User.builder().id(2L).name("Empresa").role(Role.COMPANY).build();
                Vacancy vacancy = Vacancy.builder().id(3L).title("Titulo").company(company).build();

                when(vacancyRepository.findById(3L)).thenReturn(Optional.of(vacancy));
                when(vacancySwipeRepository.findByUserIdAndVacancyId(1L, 3L)).thenReturn(Optional.empty());
                when(companyCandidateDecisionRepository.existsByCompanyIdAndCandidateIdAndVacancyIdAndDecision(2L, 1L, 3L, SwipeDecisionType.LIKE)).thenReturn(false);
                when(userRepository.findById(1L)).thenReturn(Optional.of(candidate));

                assertDoesNotThrow(() -> nullCacheManagerService.registerSwipeDecision(1L, 3L, SwipeDecisionType.LIKE));
                verify(vacancySwipeRepository).save(any(VacancySwipe.class));
            }
        @Test
        void shouldSkipEvictCacheKeyWhenCacheManagerIsNull_explicit() {
            VacancyService nullCacheManagerService = new VacancyService(
                    vacancyRepository,
                    userRepository,
                    matchingService,
                    profileRepository,
                    recommendationCacheRepository,
                    vacancySwipeRepository,
                    companyCandidateDecisionRepository,
                    null);
            assertDoesNotThrow(() ->
                    ReflectionTestUtils.invokeMethod(nullCacheManagerService, "evictCacheKey", "vacanciesForUser", 42L));
        }

        @Test
        void shouldUseVacanteAsFallbackWhenVacancyTitleIsBlank() {
            User candidate = User.builder().id(321L).name("Test Candidate").role(Role.CANDIDATE).build();
            User company = User.builder().id(654L).name("Test Company").role(Role.COMPANY).build();
            Vacancy vacancy = Vacancy.builder().id(987L).title("   ").company(company).build();

            when(vacancyRepository.findById(987L)).thenReturn(Optional.of(vacancy));
            when(vacancySwipeRepository.findByUserIdAndVacancyId(321L, 987L)).thenReturn(Optional.empty());
            when(companyCandidateDecisionRepository.existsByCompanyIdAndCandidateIdAndVacancyIdAndDecision(
                654L, 321L, 987L, SwipeDecisionType.LIKE)).thenReturn(true);
            when(userRepository.findById(321L)).thenReturn(Optional.of(candidate));

            ReflectionTestUtils.setField(vacancyService, "chatRealtimeService", chatRealtimeService);

            vacancyService.registerSwipeDecision(321L, 987L, SwipeDecisionType.LIKE);

            // Capture the notification payload to verify fallback title
            verify(chatRealtimeService, atLeastOnce()).publishToUser(eq(654L), argThat(event -> {
                Object payload = event.getPayload();
                if (payload instanceof java.util.Map<?, ?> map) {
                    Object title = map.get("vacancyTitle");
                    return "Vacante".equals(title);
                }
                return false;
            }));
        }
    @Test
    void shouldUseVacanteAsFallbackWhenVacancyTitleIsNull() {
        User candidate = User.builder().id(123L).name("Test Candidate").role(Role.CANDIDATE).build();
        User company = User.builder().id(456L).name("Test Company").role(Role.COMPANY).build();
        Vacancy vacancy = Vacancy.builder().id(789L).title(null).company(company).build();

        when(vacancyRepository.findById(789L)).thenReturn(Optional.of(vacancy));
        when(vacancySwipeRepository.findByUserIdAndVacancyId(123L, 789L)).thenReturn(Optional.empty());
        when(companyCandidateDecisionRepository.existsByCompanyIdAndCandidateIdAndVacancyIdAndDecision(
            456L, 123L, 789L, SwipeDecisionType.LIKE)).thenReturn(true);
        when(userRepository.findById(123L)).thenReturn(Optional.of(candidate));

        ReflectionTestUtils.setField(vacancyService, "chatRealtimeService", chatRealtimeService);

        vacancyService.registerSwipeDecision(123L, 789L, SwipeDecisionType.LIKE);

        // Capture the notification payload to verify fallback title
        verify(chatRealtimeService, atLeastOnce()).publishToUser(eq(456L), argThat(event -> {
            Object payload = event.getPayload();
            if (payload instanceof java.util.Map<?, ?> map) {
                Object title = map.get("vacancyTitle");
                return "Vacante".equals(title);
            }
            return false;
        }));
    }

    @Mock
    private VacancyRepository vacancyRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private MatchingService matchingService;

    @Mock
    private ProfileRepository profileRepository;

    @Mock
    private RecommendationCacheRepository recommendationCacheRepository;

    @Mock
    private VacancySwipeRepository vacancySwipeRepository;

    @Mock
    private CompanyCandidateDecisionRepository companyCandidateDecisionRepository;

    @Mock
    private CacheManager cacheManager;

    @Mock
    private ChatRealtimeService chatRealtimeService;

    @InjectMocks
    private VacancyService vacancyService;

    private User testCompany;
    private Vacancy testVacancy;
    private CreateVacancyRequest testRequest;

    @BeforeEach
    void setUp() {
        testCompany = User.builder()
                .id(1L)
                .name("Tech Company")
                .email("company@example.com")
                .password("password123")
                .role(Role.COMPANY)
                .build();

        testVacancy = Vacancy.builder()
                .id(1L)
                .title("Senior Developer")
                .description("Looking for a senior Java developer")
                .location("Bogotá, Colombia")
                .modality(Modality.REMOTE)
                .employmentType(EmploymentType.FULL_TIME)
                .experienceLevel(ExperienceLevel.SENIOR)
                .minSalary(7000.0)
                .maxSalary(10000.0)
                .createdAt(LocalDateTime.now())
                .company(testCompany)
                .build();

        testRequest = new CreateVacancyRequest();
        testRequest.setTitle("Senior Developer");
        testRequest.setDescription("Looking for a senior Java developer");
        testRequest.setLocation("Bogotá, Colombia");
        testRequest.setModality("REMOTE");
        testRequest.setEmploymentType("FULL_TIME");
        testRequest.setExperienceLevel("SENIOR");
        testRequest.setMinSalary(7000.0);
        testRequest.setMaxSalary(10000.0);

        ReflectionTestUtils.setField(vacancyService, "chatRealtimeService", null);
    }

    private CompanyCandidateDecisionResponse registerCompanyCandidateDecision(
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
        CompanyCandidateDecisionRequest request = CompanyCandidateDecisionRequest.builder()
                .decision(decision)
                .rejectionReason(rejectionReason)
                .rejectionTags(rejectionTags)
                .missingTechnologies(missingTechnologies)
                .missingResponsibilities(missingResponsibilities)
                .missingTechnicalRequirements(missingTechnicalRequirements)
                .expectedExperienceLevel(expectedExperienceLevel)
                .aiSummary(aiSummary)
                .rejectionComment(rejectionComment)
                .build();
        return vacancyService.registerCompanyCandidateDecision(companyId, vacancyId, candidateId, request);
    }

    @Test
    void shouldReturnVacancySummariesForCompanyUser() {
        User companyUser = User.builder().id(1L).role(Role.COMPANY).build();
        VacancySummaryResponse summary = new VacancySummaryResponse(1L, "Backend", "Acme", "Bogota", "Desc", 1000.0, 2000.0);

        when(userRepository.findById(1L)).thenReturn(Optional.of(companyUser));
        when(vacancyRepository.findSummaryByCompanyId(1L)).thenReturn(List.of(summary));

        List<VacancySummaryResponse> result = vacancyService.getVacancySummariesForUser(1L);

        assertEquals(1, result.size());
        assertEquals("Backend", result.get(0).title());
    }

    @Test
    void shouldReturnAllSummariesWhenCandidateHasNoSwipes() {
        User candidateUser = User.builder().id(2L).role(Role.CANDIDATE).build();
        VacancySummaryResponse summary = new VacancySummaryResponse(3L, "Data", "Acme", "Bogota", "Desc", 1000.0, 2000.0);

        when(userRepository.findById(2L)).thenReturn(Optional.of(candidateUser));
        when(vacancySwipeRepository.findSwipedVacancyIdsByUserId(2L)).thenReturn(List.of());
        when(vacancyRepository.findAllSummaries()).thenReturn(List.of(summary));

        List<VacancySummaryResponse> result = vacancyService.getVacancySummariesForUser(2L);

        assertEquals(1, result.size());
        assertEquals(3L, result.get(0).id());
    }

    @Test
    void shouldUseOptimizedSummaryQueryWhenAvailable() {
        User candidateUser = User.builder().id(2L).role(Role.CANDIDATE).build();
        VacancySummaryResponse optimized = new VacancySummaryResponse(5L, "Optimized", "Acme", "Bogota", "Desc", 1000.0, 2000.0);

        when(userRepository.findById(2L)).thenReturn(Optional.of(candidateUser));
        when(vacancySwipeRepository.findSwipedVacancyIdsByUserId(2L)).thenReturn(List.of(1L));
        when(vacancyRepository.findSummaryNotSwipedByUser(2L)).thenReturn(List.of(optimized));

        List<VacancySummaryResponse> result = vacancyService.getVacancySummariesForUser(2L);

        assertEquals(1, result.size());
        assertEquals(5L, result.get(0).id());
        verify(vacancyRepository, never()).findAllSummaries();
    }

    @Test
    void shouldFallbackToFilteringAllSummariesWhenOptimizedIsEmpty() {
        User candidateUser = User.builder().id(2L).role(Role.CANDIDATE).build();
        VacancySummaryResponse swiped = new VacancySummaryResponse(1L, "Swiped", "Acme", "Bogota", "Desc", 1000.0, 2000.0);
        VacancySummaryResponse remaining = new VacancySummaryResponse(6L, "Remain", "Acme", "Bogota", "Desc", 1000.0, 2000.0);

        when(userRepository.findById(2L)).thenReturn(Optional.of(candidateUser));
        when(vacancySwipeRepository.findSwipedVacancyIdsByUserId(2L)).thenReturn(List.of(1L));
        when(vacancyRepository.findSummaryNotSwipedByUser(2L)).thenReturn(List.of());
        when(vacancyRepository.findAllSummaries()).thenReturn(List.of(swiped, remaining));

        List<VacancySummaryResponse> result = vacancyService.getVacancySummariesForUser(2L);

        assertEquals(1, result.size());
        assertEquals(6L, result.get(0).id());
    }

    @Test
    void shouldThrowWhenVacancySummariesRequestedForMissingUser() {
        when(userRepository.findById(404L)).thenReturn(Optional.empty());

        RuntimeException exception = assertThrows(RuntimeException.class,
                () -> vacancyService.getVacancySummariesForUser(404L));

        assertEquals(ErrorMessages.USER_NOT_FOUND, exception.getMessage());
    }

    @Test
    void shouldPublishRealtimeNotificationsAndEvictCachesOnLikeDecision() {
        User candidateUser = User.builder().id(1L).name("Candidate").role(Role.CANDIDATE).build();
        User companyUser = User.builder().id(9L).name("Company").role(Role.COMPANY).build();
        Vacancy vacancy = Vacancy.builder().id(3L).title("Platform").company(companyUser).build();

        Cache cache = org.mockito.Mockito.mock(Cache.class);
        when(cacheManager.getCache(any())).thenReturn(cache);

        ReflectionTestUtils.setField(vacancyService, "chatRealtimeService", chatRealtimeService);

        when(vacancyRepository.findById(3L)).thenReturn(Optional.of(vacancy));
        when(vacancySwipeRepository.findByUserIdAndVacancyId(1L, 3L)).thenReturn(Optional.empty());
        when(companyCandidateDecisionRepository.existsByCompanyIdAndCandidateIdAndVacancyIdAndDecision(
                9L, 1L, 3L, SwipeDecisionType.LIKE)).thenReturn(true);
        when(userRepository.findById(1L)).thenReturn(Optional.of(candidateUser));

        vacancyService.registerSwipeDecision(1L, 3L, SwipeDecisionType.LIKE);

        verify(chatRealtimeService, times(3)).publishToUser(anyLong(), any());
        verify(cache, atLeastOnce()).evict(1L);
    }

    @Test
    void shouldReturnEarlyWhenVacancyHasNoCompanyDuringSwipeInvalidation() {
        Vacancy vacancyWithoutCompany = Vacancy.builder().id(300L).title("No company").company(null).build();

        when(vacancyRepository.findById(300L)).thenReturn(Optional.of(vacancyWithoutCompany));
        when(vacancySwipeRepository.findByUserIdAndVacancyId(1L, 300L)).thenReturn(Optional.empty());

        vacancyService.registerSwipeDecision(1L, 300L, SwipeDecisionType.DISLIKE);

        verify(chatRealtimeService, never()).publishToUser(anyLong(), any());
    }

    @Test
    void shouldIgnoreSwipeInvalidationWhenCompanyIdIsNull() {
        User companyWithoutId = User.builder().id(null).name("Null Company").role(Role.COMPANY).build();
        Vacancy vacancy = Vacancy.builder().id(31L).title("Test").company(companyWithoutId).build();

        when(vacancyRepository.findById(31L)).thenReturn(Optional.of(vacancy));
        when(vacancySwipeRepository.findByUserIdAndVacancyId(201L, 31L)).thenReturn(Optional.empty());

        vacancyService.registerSwipeDecision(201L, 31L, SwipeDecisionType.DISLIKE);

        verify(chatRealtimeService, never()).publishToUser(anyLong(), any());
    }

    @Test
    void shouldSkipEvictCacheKeyWhenKeyIsNull() {
        assertDoesNotThrow(() ->
                ReflectionTestUtils.invokeMethod(vacancyService, "evictCacheKey", "vacanciesForUser", new Object[] { null }));
    }

    @Test
    void shouldSkipEvictCacheKeyWhenCacheManagerIsNull() {
        VacancyService nullCacheManagerService = new VacancyService(
                vacancyRepository,
                userRepository,
                matchingService,
                profileRepository,
                recommendationCacheRepository,
                vacancySwipeRepository,
                companyCandidateDecisionRepository,
                null);
        assertDoesNotThrow(() ->
                ReflectionTestUtils.invokeMethod(nullCacheManagerService, "evictCacheKey", "vacanciesForUser", 123L));
    }

    @Test
    void shouldRegisterCompanyDecisionWithBlankTitlesAndNames() {
        User blankCompany = User.builder().id(9L).name(" ").role(Role.COMPANY).build();
        User blankCandidate = User.builder().id(200L).name(" ").role(Role.CANDIDATE).build();
        Vacancy vacancy = Vacancy.builder().id(30L).title(" ").company(blankCompany).build();

        when(userRepository.findById(9L)).thenReturn(Optional.of(blankCompany));
        when(userRepository.findById(200L)).thenReturn(Optional.of(blankCandidate));
        when(vacancyRepository.findById(30L)).thenReturn(Optional.of(vacancy));
        when(companyCandidateDecisionRepository.findByCompanyIdAndCandidateIdAndVacancyId(9L, 200L, 30L))
            .thenReturn(Optional.empty());
        when(vacancySwipeRepository.existsByUserIdAndVacancyIdAndDecision(200L, 30L, SwipeDecisionType.LIKE))
            .thenReturn(false);
        when(recommendationCacheRepository.findByUserIdAndVacancyId(200L, 30L)).thenReturn(Optional.empty());

        CompanyCandidateDecisionResponse response = registerCompanyCandidateDecision(
            9L,
            30L,
            200L,
            SwipeDecisionType.DISLIKE,
            "Reason",
            List.of("tag"),
            List.of("java"),
            List.of("responsibility"),
            List.of("requirement"),
            "SENIOR",
            "summary",
            "comment");

        assertEquals(SwipeDecisionType.DISLIKE, response.getDecision());
        assertFalse(response.isMatched());
        }

    @Test
    void shouldSkipRealtimeNotificationWhenServiceOrTypeIsMissing() {
        assertDoesNotThrow(() -> {
            ReflectionTestUtils.setField(vacancyService, "chatRealtimeService", null);
            ReflectionTestUtils.invokeMethod(vacancyService, "publishRealtimeNotification", 1L, "notification.test", java.util.Map.of("x", 1));

            ReflectionTestUtils.setField(vacancyService, "chatRealtimeService", chatRealtimeService);
            ReflectionTestUtils.invokeMethod(vacancyService, "publishRealtimeNotification", null, "notification.test", java.util.Map.of("x", 1));
            ReflectionTestUtils.invokeMethod(vacancyService, "publishRealtimeNotification", 1L, " ", java.util.Map.of("x", 1));
        });
        verifyNoInteractions(chatRealtimeService);
    }

    @Test
    void shouldMapVacancyDetailWithNullOptionalFields() {
        Vacancy vacancy = Vacancy.builder()
                .id(900L)
                .title("Minimal")
                .description("Desc")
                .location("Remote")
                .sector(null)
                .modality(null)
                .employmentType(null)
                .experienceLevel(null)
                .technologies(null)
                .softSkills(null)
                .responsibilities(null)
                .technicalRequirements(null)
                .minSalary(null)
                .maxSalary(null)
                .benefits(null)
                .company(null)
                .build();

        when(vacancyRepository.findById(900L)).thenReturn(Optional.of(vacancy));

        VacancyDetailResponse response = vacancyService.getVacancyById(900L);

        assertNotNull(response);
        assertEquals(null, response.modality());
        assertEquals(null, response.employmentType());
        assertEquals(null, response.experienceLevel());
        assertTrue(response.technologies().isEmpty());
        assertTrue(response.softSkills().isEmpty());
        assertTrue(response.responsibilities().isEmpty());
        assertTrue(response.technicalRequirements().isEmpty());
        assertTrue(response.benefits().isEmpty());
        assertEquals(null, response.companyId());
        assertEquals(null, response.companyName());
    }

    @Test
    void shouldCreateVacancySuccessfully() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(testCompany));
        when(vacancyRepository.save(any(Vacancy.class))).thenReturn(testVacancy);

        VacancyDetailResponse created = vacancyService.createVacancy(testRequest, 1L);

        assertNotNull(created);
        assertEquals("Senior Developer", created.title());
        assertEquals(1L, created.companyId());
        verify(userRepository, times(1)).findById(1L);
        verify(vacancyRepository, times(1)).save(any(Vacancy.class));
    }

    @Test
    void shouldThrowExceptionWhenCompanyNotFound() {
        when(userRepository.findById(999L)).thenReturn(Optional.empty());

        assertThrows(RuntimeException.class, () ->
                vacancyService.createVacancy(testRequest, 999L));

        verify(userRepository, times(1)).findById(999L);
        verify(vacancyRepository, times(0)).save(any(Vacancy.class));
    }

    @Test
    void shouldThrowExceptionWhenUserIsNotCompany() {
        User candidate = User.builder()
                .id(2L)
                .name("John Doe")
                .email("john@example.com")
                .role(Role.CANDIDATE)
                .build();

        when(userRepository.findById(2L)).thenReturn(Optional.of(candidate));

        assertThrows(IllegalArgumentException.class, () ->
                vacancyService.createVacancy(testRequest, 2L));

        verify(vacancyRepository, times(0)).save(any(Vacancy.class));
    }

    @Test
    void shouldReturnVacancyById() {
        when(vacancyRepository.findById(1L)).thenReturn(Optional.of(testVacancy));

        VacancyDetailResponse found = vacancyService.getVacancyById(1L);

        assertNotNull(found);
        assertEquals("Senior Developer", found.title());
        assertEquals(1L, found.id());
        verify(vacancyRepository, times(1)).findById(1L);
    }

    @Test
    void shouldThrowExceptionWhenVacancyNotFound() {
        when(vacancyRepository.findById(2L)).thenReturn(Optional.empty());

        assertThrows(RuntimeException.class, () ->
                vacancyService.getVacancyById(2L));

        verify(vacancyRepository, times(1)).findById(2L);
    }

    @Test
    void shouldReturnAllVacancies() {
        Vacancy another = Vacancy.builder()
                .id(2L)
                .title("Junior Developer")
                .description("Looking for a junior developer")
                .location("Medellín, Colombia")
                .modality(Modality.HYBRID)
                .employmentType(EmploymentType.PART_TIME)
                .experienceLevel(ExperienceLevel.JUNIOR)
                .minSalary(3000.0)
                .maxSalary(5000.0)
                .createdAt(LocalDateTime.now())
                .company(testCompany)
                .build();

        when(vacancyRepository.findAll()).thenReturn(Arrays.asList(testVacancy, another));

        List<Vacancy> all = vacancyService.getAllVacancies();

        assertNotNull(all);
        assertEquals(2, all.size());
        verify(vacancyRepository, times(1)).findAll();
    }

    @Test
    void shouldReturnAllVacanciesForUserWhenNoSwipesExist() {
        User candidate = User.builder()
            .id(1L)
            .name("Candidate User")
            .email("candidate@example.com")
            .role(Role.CANDIDATE)
            .build();

        when(userRepository.findById(1L)).thenReturn(Optional.of(candidate));
        when(vacancySwipeRepository.findSwipedVacancyIdsByUserId(1L)).thenReturn(List.of());
        when(vacancyRepository.findAll()).thenReturn(Arrays.asList(testVacancy));

        List<Vacancy> result = vacancyService.getAllVacanciesForUser(1L);

        assertEquals(1, result.size());
        assertEquals(testVacancy.getId(), result.get(0).getId());
        verify(vacancyRepository, times(1)).findAll();
    }

    @Test
    void shouldFilterOutSwipedVacanciesForUser() {
        User candidate = User.builder()
            .id(1L)
            .name("Candidate User")
            .email("candidate@example.com")
            .role(Role.CANDIDATE)
            .build();

        Vacancy second = Vacancy.builder()
                .id(2L)
                .title("Junior Developer")
                .description("Looking for a junior developer")
                .location("Medellin, Colombia")
                .modality(Modality.HYBRID)
                .employmentType(EmploymentType.PART_TIME)
                .experienceLevel(ExperienceLevel.JUNIOR)
                .createdAt(LocalDateTime.now())
                .company(testCompany)
                .build();

            when(userRepository.findById(1L)).thenReturn(Optional.of(candidate));
        when(vacancySwipeRepository.findSwipedVacancyIdsByUserId(1L)).thenReturn(List.of(1L));
        when(vacancyRepository.findAll()).thenReturn(Arrays.asList(testVacancy, second));

        List<Vacancy> result = vacancyService.getAllVacanciesForUser(1L);

        assertEquals(1, result.size());
        assertEquals(2L, result.get(0).getId());
        verify(vacancyRepository, times(1)).findAll();
    }

    @Test
    void shouldThrowWhenUserNotFoundForGetAllVacanciesForUser() {
        when(userRepository.findById(404L)).thenReturn(Optional.empty());

        RuntimeException exception = assertThrows(RuntimeException.class,
                () -> vacancyService.getAllVacanciesForUser(404L));

        assertEquals("User not found", exception.getMessage());
        verify(vacancyRepository, never()).findAll();
        verify(vacancyRepository, never()).findAllByCompanyId(anyLong());
    }

    @Test
    void shouldReturnCompanyVacanciesForCompanyUser() {
        User company = User.builder()
                .id(1L)
                .name("Company")
                .email("company@example.com")
                .role(Role.COMPANY)
                .build();

        when(userRepository.findById(1L)).thenReturn(Optional.of(company));
        when(vacancyRepository.findAllByCompanyId(1L)).thenReturn(List.of(testVacancy));

        List<Vacancy> result = vacancyService.getAllVacanciesForUser(1L);

        assertEquals(1, result.size());
        assertEquals(testVacancy.getId(), result.get(0).getId());
        verify(vacancyRepository, times(1)).findAllByCompanyId(1L);
        verify(vacancySwipeRepository, never()).findSwipedVacancyIdsByUserId(anyLong());
    }

        @Test
        void shouldReturnEmptyCompanyLikeActivityWhenCompanyHasNoVacancies() {
        when(vacancyRepository.findAllByCompanyId(1L)).thenReturn(List.of());

        List<?> result = vacancyService.getCompanyLikeActivity(1L, 20);

        assertTrue(result.isEmpty());
        verify(vacancySwipeRepository, never()).findRecentByVacancyIdsAndDecision(any(), any(), any());
        }

        @Test
        void shouldUseLegacyFallbackWhenCompanyCandidateDecisionRepositoryIsNull() {
        Vacancy companyVacancy = Vacancy.builder()
            .id(15L)
            .title("Backend Developer")
            .company(testCompany)
            .build();

        when(vacancyRepository.findAllByCompanyId(1L)).thenReturn(List.of(companyVacancy));
        when(vacancySwipeRepository.findRecentByVacancyIdsAndDecision(any(), eq(SwipeDecisionType.LIKE), any()))
            .thenReturn(List.of());

        ReflectionTestUtils.setField(vacancyService, "companyCandidateDecisionRepository", null);

        var activity = vacancyService.getCompanyLikeActivity(1L, 20);

        assertTrue(activity.isEmpty());
        }

        @Test
        void shouldReturnCompanyLikeActivityWithCandidateNameFallback() {
        Vacancy companyVacancy = Vacancy.builder()
            .id(10L)
            .title("Backend Developer")
            .company(testCompany)
            .build();

        VacancySwipe likeKnownUser = VacancySwipe.builder()
            .userId(100L)
            .vacancyId(10L)
            .decision(SwipeDecisionType.LIKE)
            .updatedAt(LocalDateTime.now())
            .build();

        VacancySwipe likeUnknownUser = VacancySwipe.builder()
            .userId(101L)
            .vacancyId(10L)
            .decision(SwipeDecisionType.LIKE)
            .updatedAt(LocalDateTime.now())
            .build();

        when(vacancyRepository.findAllByCompanyId(1L)).thenReturn(List.of(companyVacancy));
        when(vacancySwipeRepository.findRecentByVacancyIdsAndDecision(any(), eq(SwipeDecisionType.LIKE), any()))
            .thenReturn(List.of(likeKnownUser, likeUnknownUser));
        when(userRepository.findAllById(any())).thenReturn(List.of(User.builder().id(100L).name("Ana").build()));

        var activity = vacancyService.getCompanyLikeActivity(1L, 20);

        assertEquals(2, activity.size());
        assertEquals("Ana", activity.get(0).getCandidateName());
        assertEquals("Usuario 101", activity.get(1).getCandidateName());
        assertEquals("Backend Developer", activity.get(0).getVacancyTitle());
        }

        @Test
        void shouldHandleSwipeDecisionLikeWithBlankTitleAndBlankCandidateName() {
        User blankNameCandidate = User.builder().id(200L).name(" ").role(Role.CANDIDATE).build();
        User company = User.builder().id(9L).name("Company").role(Role.COMPANY).build();
        Vacancy vacancy = Vacancy.builder().id(30L).title(" ").company(company).build();

        when(vacancyRepository.findById(30L)).thenReturn(Optional.of(vacancy));
        when(vacancySwipeRepository.findByUserIdAndVacancyId(200L, 30L)).thenReturn(Optional.empty());
        when(userRepository.findById(200L)).thenReturn(Optional.of(blankNameCandidate));
        when(companyCandidateDecisionRepository.existsByCompanyIdAndCandidateIdAndVacancyIdAndDecision(
            9L, 200L, 30L, SwipeDecisionType.LIKE)).thenReturn(false);

        vacancyService.registerSwipeDecision(200L, 30L, SwipeDecisionType.LIKE);

        verify(chatRealtimeService, never()).publishToUser(anyLong(), any());
        }

        @Test
        void shouldIgnoreLikeDecisionWhenVacancyHasNoCompany() {
        Vacancy vacancyWithoutCompany = Vacancy.builder().id(31L).title("Some title").company(null).build();

        when(vacancyRepository.findById(31L)).thenReturn(Optional.of(vacancyWithoutCompany));
        when(vacancySwipeRepository.findByUserIdAndVacancyId(201L, 31L)).thenReturn(Optional.empty());

        vacancyService.registerSwipeDecision(201L, 31L, SwipeDecisionType.LIKE);

        verify(chatRealtimeService, never()).publishToUser(anyLong(), any());
        }

        @Test
        void shouldUseDefaultLimitWhenCompanyActivityLimitIsNull() {
        Vacancy companyVacancy = Vacancy.builder()
            .id(10L)
            .title("Backend Developer")
            .company(testCompany)
            .build();

        when(vacancyRepository.findAllByCompanyId(1L)).thenReturn(List.of(companyVacancy));
        when(vacancySwipeRepository.findRecentByVacancyIdsAndDecision(
                any(),
                eq(SwipeDecisionType.LIKE),
                argThat(page -> page.getPageNumber() == 0 && page.getPageSize() == 20)))
            .thenReturn(List.of());

        var activity = vacancyService.getCompanyLikeActivity(1L, null);

        assertTrue(activity.isEmpty());
        }

        @Test
        void shouldReturnCompanyVacancyPipelineSortedByApplicantsCountDesc() {
        Vacancy first = Vacancy.builder().id(1L).title("First").company(testCompany).build();
        Vacancy second = Vacancy.builder().id(2L).title("Second").company(testCompany).build();

        when(vacancyRepository.findAllByCompanyId(1L)).thenReturn(List.of(first, second));
        when(vacancySwipeRepository.countPendingLikesByVacancyIds(1L, List.of(1L, 2L)))
            .thenReturn(List.of(
                new Object[] { 1L, 1L },
                new Object[] { 2L, 3L }
            ));

        var pipeline = vacancyService.getCompanyVacancyPipeline(1L);

        assertEquals(2, pipeline.size());
        assertEquals(2L, pipeline.get(0).getVacancyId());
        assertEquals(3, pipeline.get(0).getApplicantsCount());
        assertEquals(1L, pipeline.get(1).getVacancyId());
        }

        @Test
        void shouldReturnEmptyPipelineWhenCompanyHasNoVacancies() {
        when(vacancyRepository.findAllByCompanyId(1L)).thenReturn(List.of());

        var pipeline = vacancyService.getCompanyVacancyPipeline(1L);

        assertTrue(pipeline.isEmpty());
        verify(vacancySwipeRepository, never()).countPendingLikesByVacancyIds(anyLong(), any());
        }

        @Test
        void shouldReturnApplicantsByVacancyWithCompatibilityWhenCacheExists() {
        User company = User.builder().id(1L).role(Role.COMPANY).build();
        Vacancy companyVacancy = Vacancy.builder().id(77L).title("Vacancy").company(company).build();

        VacancySwipe like = VacancySwipe.builder()
            .userId(200L)
            .vacancyId(77L)
            .decision(SwipeDecisionType.LIKE)
            .updatedAt(LocalDateTime.now())
            .build();

        RecommendationCache cache = RecommendationCache.builder()
            .userId(200L)
            .vacancyId(77L)
            .compatibilityPercentage(92.0f)
            .compatibilityLevel("high")
            .feedback("Great fit")
            .build();

        when(vacancyRepository.findById(77L)).thenReturn(Optional.of(companyVacancy));
        when(vacancySwipeRepository.findByVacancyIdAndDecisionOrderByUpdatedAtDesc(eq(77L), eq(SwipeDecisionType.LIKE), any()))
            .thenReturn(List.of(like));
        when(recommendationCacheRepository.findByVacancyIdAndUserIdIn(eq(77L), any()))
            .thenReturn(List.of(cache));
        when(userRepository.findAllById(any())).thenReturn(List.of(User.builder().id(200L).name("Carlos").build()));

        var applicants = vacancyService.getApplicantsByVacancy(1L, 77L, 10);

        assertEquals(1, applicants.size());
        assertEquals("Carlos", applicants.get(0).getCandidateName());
        assertEquals(92.0f, applicants.get(0).getCompatibilityPercentage());
        assertEquals("high", applicants.get(0).getCompatibilityLevel());
        assertEquals("Great fit", applicants.get(0).getFeedback());
        }

        @Test
        void shouldUseDefaultLimitWhenApplicantsLimitIsNull() {
        User company = User.builder().id(1L).role(Role.COMPANY).build();
        Vacancy companyVacancy = Vacancy.builder().id(81L).title("Vacancy").company(company).build();

        when(vacancyRepository.findById(81L)).thenReturn(Optional.of(companyVacancy));
        when(vacancySwipeRepository.findByVacancyIdAndDecisionOrderByUpdatedAtDesc(
                eq(81L),
                eq(SwipeDecisionType.LIKE),
                argThat(page -> page.getPageNumber() == 0 && page.getPageSize() == 50)))
            .thenReturn(List.of());

        var applicants = vacancyService.getApplicantsByVacancy(1L, 81L, null);

        assertTrue(applicants.isEmpty());
        }

        @Test
        void shouldClampApplicantsLimitTo200WhenLimitExceedsMaximum() {
        User company = User.builder().id(1L).role(Role.COMPANY).build();
        Vacancy companyVacancy = Vacancy.builder().id(82L).title("Vacancy").company(company).build();

        when(vacancyRepository.findById(82L)).thenReturn(Optional.of(companyVacancy));
        when(vacancySwipeRepository.findByVacancyIdAndDecisionOrderByUpdatedAtDesc(
                eq(82L),
                eq(SwipeDecisionType.LIKE),
                argThat(page -> page.getPageNumber() == 0 && page.getPageSize() == 200)))
            .thenReturn(List.of());

        var applicants = vacancyService.getApplicantsByVacancy(1L, 82L, 1000);

        assertTrue(applicants.isEmpty());
        }

        @Test
        void shouldReturnEmptyApplicantsWhenVacancyHasNoLikes() {
        User company = User.builder().id(1L).role(Role.COMPANY).build();
        Vacancy companyVacancy = Vacancy.builder().id(78L).title("Vacancy").company(company).build();

        when(vacancyRepository.findById(78L)).thenReturn(Optional.of(companyVacancy));
        when(vacancySwipeRepository.findByVacancyIdAndDecisionOrderByUpdatedAtDesc(eq(78L), eq(SwipeDecisionType.LIKE), any()))
            .thenReturn(List.of());

        var applicants = vacancyService.getApplicantsByVacancy(1L, 78L, 10);

        assertTrue(applicants.isEmpty());
        verify(recommendationCacheRepository, never()).findByVacancyIdAndUserIdIn(anyLong(), any());
        }

        @Test
        void shouldReturnApplicantsWithNullCompatibilityWhenCacheIsMissing() {
        User company = User.builder().id(1L).role(Role.COMPANY).build();
        Vacancy companyVacancy = Vacancy.builder().id(83L).title("Vacancy").company(company).build();

        VacancySwipe like = VacancySwipe.builder()
            .userId(300L)
            .vacancyId(83L)
            .decision(SwipeDecisionType.LIKE)
            .updatedAt(LocalDateTime.now())
            .build();

        when(vacancyRepository.findById(83L)).thenReturn(Optional.of(companyVacancy));
        when(vacancySwipeRepository.findByVacancyIdAndDecisionOrderByUpdatedAtDesc(eq(83L), eq(SwipeDecisionType.LIKE), any()))
            .thenReturn(List.of(like));
        when(recommendationCacheRepository.findByVacancyIdAndUserIdIn(eq(83L), any()))
            .thenReturn(List.of());
        when(userRepository.findAllById(any())).thenReturn(List.of());

        var applicants = vacancyService.getApplicantsByVacancy(1L, 83L, 10);

        assertEquals(1, applicants.size());
        assertEquals("Usuario 300", applicants.get(0).getCandidateName());
        assertEquals(null, applicants.get(0).getCompatibilityPercentage());
        assertEquals(null, applicants.get(0).getCompatibilityLevel());
        assertEquals(null, applicants.get(0).getFeedback());
        }

        @Test
        void shouldThrowWhenCompanyDoesNotOwnVacancyForApplicantsQuery() {
        User otherCompany = User.builder().id(2L).role(Role.COMPANY).build();
        Vacancy companyVacancy = Vacancy.builder().id(79L).title("Vacancy").company(otherCompany).build();
        when(vacancyRepository.findById(79L)).thenReturn(Optional.of(companyVacancy));

        RuntimeException exception = assertThrows(RuntimeException.class,
            () -> vacancyService.getApplicantsByVacancy(1L, 79L, 10));

        assertEquals("Vacancy not found", exception.getMessage());
        }

        @Test
        void shouldThrowWhenVacancyIsMissingForApplicantsQuery() {
        when(vacancyRepository.findById(999L)).thenReturn(Optional.empty());

        RuntimeException exception = assertThrows(RuntimeException.class,
            () -> vacancyService.getApplicantsByVacancy(1L, 999L, 10));

        assertEquals("Vacancy not found", exception.getMessage());
        verify(vacancySwipeRepository, never())
            .findByVacancyIdAndDecisionOrderByUpdatedAtDesc(anyLong(), any(), any());
        }

        @Test
        void shouldThrowWhenVacancyHasNoCompanyForApplicantsQuery() {
        Vacancy orphanVacancy = Vacancy.builder().id(80L).title("Orphan").company(null).build();
        when(vacancyRepository.findById(80L)).thenReturn(Optional.of(orphanVacancy));

        RuntimeException exception = assertThrows(RuntimeException.class,
            () -> vacancyService.getApplicantsByVacancy(1L, 80L, 10));

        assertEquals("Vacancy not found", exception.getMessage());
        verify(vacancySwipeRepository, never())
            .findByVacancyIdAndDecisionOrderByUpdatedAtDesc(anyLong(), any(), any());
        }

    @Test
    void shouldUpdateVacancy() {
        CreateVacancyRequest updateRequest = new CreateVacancyRequest();
        updateRequest.setTitle("Lead Developer");
        updateRequest.setDescription("Lead role");
        updateRequest.setLocation("Cali, Colombia");
        updateRequest.setModality("ON_SITE");
        updateRequest.setEmploymentType("FULL_TIME");
        updateRequest.setExperienceLevel("SENIOR");
        updateRequest.setMinSalary(9000.0);
        updateRequest.setMaxSalary(12000.0);

        when(vacancyRepository.findById(1L)).thenReturn(Optional.of(testVacancy));
        when(vacancyRepository.save(any(Vacancy.class))).thenAnswer(i -> i.getArgument(0));

        VacancyDetailResponse updated = vacancyService.updateVacancy(1L, updateRequest);

        assertNotNull(updated);
        assertEquals("Lead Developer", updated.title());
        assertEquals("Lead role", updated.description());
        assertEquals(9000.0, updated.minSalary());
        assertEquals(12000.0, updated.maxSalary());
        verify(vacancyRepository, times(1)).findById(1L);
        verify(vacancyRepository, times(1)).save(any(Vacancy.class));
    }

    @Test
    void shouldDeleteVacancy() {
        when(vacancyRepository.findById(1L)).thenReturn(Optional.of(testVacancy));

        vacancyService.deleteVacancy(1L);

        verify(vacancyRepository, times(1)).findById(1L);
        verify(vacancyRepository, times(1)).delete(testVacancy);
    }

    @Test
    void shouldThrowWhenUpdatingVacancyThatDoesNotExist() {
        when(vacancyRepository.findById(404L)).thenReturn(Optional.empty());

        RuntimeException exception = assertThrows(RuntimeException.class,
                () -> vacancyService.updateVacancy(404L, testRequest));

        assertEquals("Vacancy not found", exception.getMessage());
        verify(vacancyRepository, times(1)).findById(404L);
        verify(vacancyRepository, never()).save(any(Vacancy.class));
    }

    @Test
    void shouldThrowWhenDeletingVacancyThatDoesNotExist() {
        when(vacancyRepository.findById(405L)).thenReturn(Optional.empty());

        RuntimeException exception = assertThrows(RuntimeException.class,
                () -> vacancyService.deleteVacancy(405L));

        assertEquals("Vacancy not found", exception.getMessage());
        verify(vacancyRepository, times(1)).findById(405L);
        verify(vacancyRepository, never()).delete(any(Vacancy.class));
    }

    @Test
    void shouldRegisterSwipeDecisionWhenSwipeDoesNotExist() {
        when(vacancyRepository.findById(1L)).thenReturn(Optional.of(testVacancy));
        when(vacancySwipeRepository.findByUserIdAndVacancyId(1L, 1L)).thenReturn(Optional.empty());

        vacancyService.registerSwipeDecision(1L, 1L, SwipeDecisionType.LIKE);

        verify(vacancySwipeRepository, times(1)).save(any(VacancySwipe.class));
    }

    @Test
    void shouldUpdateSwipeDecisionWhenSwipeAlreadyExists() {
        VacancySwipe existing = VacancySwipe.builder()
                .id(99L)
                .userId(1L)
                .vacancyId(1L)
                .decision(SwipeDecisionType.DISLIKE)
                .build();

        when(vacancyRepository.findById(1L)).thenReturn(Optional.of(testVacancy));
        when(vacancySwipeRepository.findByUserIdAndVacancyId(1L, 1L)).thenReturn(Optional.of(existing));

        vacancyService.registerSwipeDecision(1L, 1L, SwipeDecisionType.LIKE);

        assertEquals(SwipeDecisionType.LIKE, existing.getDecision());
        verify(vacancySwipeRepository, times(1)).save(existing);
    }

    @Test
    void shouldThrowWhenRegisteringSwipeForMissingVacancy() {
        when(vacancyRepository.findById(404L)).thenReturn(Optional.empty());

        RuntimeException exception = assertThrows(RuntimeException.class,
                () -> vacancyService.registerSwipeDecision(1L, 404L, SwipeDecisionType.LIKE));

        assertEquals("Vacancy not found", exception.getMessage());
        verify(vacancySwipeRepository, never()).save(any(VacancySwipe.class));
    }

        @Test
        void shouldReturnRecommendedVacanciesSortedAndFilteredByScore() {
        Vacancy second = Vacancy.builder()
            .id(2L)
            .title("Junior Developer")
            .description("Looking for a junior developer")
            .location("Medellín, Colombia")
            .modality(Modality.HYBRID)
            .employmentType(EmploymentType.PART_TIME)
            .experienceLevel(ExperienceLevel.JUNIOR)
            .minSalary(3000.0)
            .maxSalary(5000.0)
            .createdAt(LocalDateTime.now())
            .company(testCompany)
            .build();

        when(vacancyRepository.findAll()).thenReturn(Arrays.asList(testVacancy, second));
        when(profileRepository.findUpdatedAtByUserId(1L)).thenReturn(Optional.of(LocalDateTime.now()));
        when(vacancySwipeRepository.findSwipedVacancyIdsByUserId(1L)).thenReturn(List.of());
        when(recommendationCacheRepository.findByUserIdAndVacancyId(any(Long.class), any(Long.class)))
            .thenReturn(Optional.empty());
        when(matchingService.calculateMatch(1L, 1L)).thenReturn(MatchingResponse.builder()
            .similarityScore(0.91)
            .compatibilityPercentage(88.0f)
            .compatibilityLevel("high")
            .feedback("Great fit")
            .usedLlmFeedback(true)
            .build());
        when(matchingService.calculateMatch(1L, 2L)).thenReturn(MatchingResponse.builder()
            .similarityScore(0.72)
            .compatibilityPercentage(72.0f)
            .compatibilityLevel("medium")
            .feedback("Good fit")
            .usedLlmFeedback(false)
            .build());

        List<VacancyRecommendationResponse> recommendations = vacancyService.getRecommendedVacancies(1L, 70.0f, 10);

        assertEquals(2, recommendations.size());
        assertEquals(88.0f, recommendations.get(0).getCompatibilityPercentage());
        assertEquals(72.0f, recommendations.get(1).getCompatibilityPercentage());
        assertTrue(recommendations.stream().allMatch(item -> item.getCompatibilityPercentage() >= 70.0f));
        verify(vacancyRepository, times(1)).findAll();
        verify(matchingService, times(1)).calculateMatch(1L, 1L);
        verify(matchingService, times(1)).calculateMatch(1L, 2L);
        }

        @Test
        void shouldLimitRecommendedVacancies() {
        Vacancy second = Vacancy.builder()
            .id(2L)
            .title("Junior Developer")
            .description("Looking for a junior developer")
            .location("Medellín, Colombia")
            .modality(Modality.HYBRID)
            .employmentType(EmploymentType.PART_TIME)
            .experienceLevel(ExperienceLevel.JUNIOR)
            .minSalary(3000.0)
            .maxSalary(5000.0)
            .createdAt(LocalDateTime.now())
            .company(testCompany)
            .build();

        when(vacancyRepository.findAll()).thenReturn(Arrays.asList(testVacancy, second));
        when(profileRepository.findUpdatedAtByUserId(1L)).thenReturn(Optional.of(LocalDateTime.now()));
        when(vacancySwipeRepository.findSwipedVacancyIdsByUserId(1L)).thenReturn(List.of());
        when(recommendationCacheRepository.findByUserIdAndVacancyId(any(Long.class), any(Long.class)))
            .thenReturn(Optional.empty());
        when(matchingService.calculateMatch(1L, 1L)).thenReturn(MatchingResponse.builder()
            .similarityScore(0.91)
            .compatibilityPercentage(88.0f)
            .compatibilityLevel("high")
            .feedback("Great fit")
            .usedLlmFeedback(true)
            .build());
        when(matchingService.calculateMatch(1L, 2L)).thenReturn(MatchingResponse.builder()
            .similarityScore(0.72)
            .compatibilityPercentage(72.0f)
            .compatibilityLevel("medium")
            .feedback("Good fit")
            .usedLlmFeedback(false)
            .build());

        List<VacancyRecommendationResponse> recommendations = vacancyService.getRecommendedVacancies(1L, 0.0f, 1);

        assertEquals(1, recommendations.size());
        assertEquals(88.0f, recommendations.get(0).getCompatibilityPercentage());
        }

        @Test
        void shouldFilterOutRecommendationsBelowMinScore() {
        Vacancy second = Vacancy.builder()
            .id(2L)
            .title("Junior Developer")
            .description("Looking for a junior developer")
            .location("Medellín, Colombia")
            .modality(Modality.HYBRID)
            .employmentType(EmploymentType.PART_TIME)
            .experienceLevel(ExperienceLevel.JUNIOR)
            .company(testCompany)
            .build();

        when(vacancyRepository.findAll()).thenReturn(Arrays.asList(testVacancy, second));
        when(profileRepository.findUpdatedAtByUserId(1L)).thenReturn(Optional.of(LocalDateTime.now()));
        when(vacancySwipeRepository.findSwipedVacancyIdsByUserId(1L)).thenReturn(List.of());
        when(recommendationCacheRepository.findByUserIdAndVacancyId(any(Long.class), any(Long.class)))
            .thenReturn(Optional.empty());
        when(matchingService.calculateMatch(1L, 1L)).thenReturn(MatchingResponse.builder()
            .compatibilityPercentage(50.0f)
            .compatibilityLevel("low")
            .similarityScore(0.5)
            .feedback("Weak fit")
            .build());
        when(matchingService.calculateMatch(1L, 2L)).thenReturn(MatchingResponse.builder()
            .compatibilityPercentage(80.0f)
            .compatibilityLevel("high")
            .similarityScore(0.8)
            .feedback("Strong fit")
            .build());

        List<VacancyRecommendationResponse> recommendations = vacancyService.getRecommendedVacancies(1L, 70.0f, 10);

        assertEquals(1, recommendations.size());
        assertEquals(2L, recommendations.get(0).getVacancyId());
        }

        @Test
        void shouldUseDefaultMinScoreAndLimitWhenNull() {
        Vacancy second = Vacancy.builder()
            .id(2L)
            .title("Junior Developer")
            .description("Looking for a junior developer")
            .location("Medellín, Colombia")
            .modality(Modality.HYBRID)
            .employmentType(EmploymentType.PART_TIME)
            .experienceLevel(ExperienceLevel.JUNIOR)
            .company(testCompany)
            .build();

        when(vacancyRepository.findAll()).thenReturn(Arrays.asList(testVacancy, second));
        when(profileRepository.findUpdatedAtByUserId(1L)).thenReturn(Optional.of(LocalDateTime.now()));
        when(vacancySwipeRepository.findSwipedVacancyIdsByUserId(1L)).thenReturn(List.of());
        when(recommendationCacheRepository.findByUserIdAndVacancyId(any(Long.class), any(Long.class)))
            .thenReturn(Optional.empty());
        when(matchingService.calculateMatch(1L, 1L)).thenReturn(MatchingResponse.builder()
            .similarityScore(0.91)
            .compatibilityPercentage(null)
            .compatibilityLevel("unknown")
            .feedback("No score")
            .build());
        when(matchingService.calculateMatch(1L, 2L)).thenReturn(MatchingResponse.builder()
            .similarityScore(0.72)
            .compatibilityPercentage(72.0f)
            .compatibilityLevel("medium")
            .feedback("Good fit")
            .build());

        List<VacancyRecommendationResponse> recommendations = vacancyService.getRecommendedVacancies(1L, null, null);

        assertEquals(2, recommendations.size());
        assertTrue(recommendations.stream().anyMatch(r -> Float.valueOf(72.0f).equals(r.getCompatibilityPercentage())));
        assertTrue(recommendations.stream().anyMatch(r -> r.getCompatibilityPercentage() == null));
        }

        @Test
        void shouldThrowWhenProfileIsMissingForRecommendations() {
        when(vacancyRepository.findAll()).thenReturn(List.of(testVacancy));
        when(profileRepository.findUpdatedAtByUserId(1L)).thenReturn(Optional.empty());

        RuntimeException exception = assertThrows(RuntimeException.class,
            () -> vacancyService.getRecommendedVacancies(1L, 0.0f, 10));

        assertEquals("Profile not found", exception.getMessage());
        }

        @Test
        void shouldUseCachedRecommendationWhenCacheIsValid() {
        LocalDateTime profileUpdatedAt = LocalDateTime.now().minusMinutes(1);
        testVacancy.setCreatedAt(LocalDateTime.now().minusMinutes(2));

        RecommendationCache cache = RecommendationCache.builder()
            .userId(1L)
            .vacancyId(1L)
            .similarityScore(0.95)
            .compatibilityPercentage(95.0f)
            .compatibilityLevel("high")
            .feedback("From cache")
            .usedLlmFeedback(true)
            .sourceProfileUpdatedAt(profileUpdatedAt)
            .sourceVacancyUpdatedAt(testVacancy.getCreatedAt())
            .updatedAt(Instant.now())
            .build();

        when(vacancyRepository.findAll()).thenReturn(List.of(testVacancy));
        when(profileRepository.findUpdatedAtByUserId(1L)).thenReturn(Optional.of(profileUpdatedAt));
        when(vacancySwipeRepository.findSwipedVacancyIdsByUserId(1L)).thenReturn(List.of());
        when(recommendationCacheRepository.findByUserIdAndVacancyId(1L, 1L)).thenReturn(Optional.of(cache));

        List<VacancyRecommendationResponse> recommendations = vacancyService.getRecommendedVacancies(1L, 0.0f, 10);

        assertEquals(1, recommendations.size());
        assertEquals(95.0f, recommendations.get(0).getCompatibilityPercentage());
        verify(matchingService, never()).calculateMatch(any(Long.class), any(Long.class));
        verify(recommendationCacheRepository, never()).save(any(RecommendationCache.class));
        }

        @Test
        void shouldRecalculateWhenCacheUpdatedAtIsNull() {
        LocalDateTime profileUpdatedAt = LocalDateTime.now().minusMinutes(1);
        testVacancy.setCreatedAt(LocalDateTime.now().minusMinutes(2));

        RecommendationCache cache = RecommendationCache.builder()
            .userId(1L)
            .vacancyId(1L)
            .sourceProfileUpdatedAt(profileUpdatedAt)
            .sourceVacancyUpdatedAt(testVacancy.getCreatedAt())
            .updatedAt(null)
            .build();

        MatchingResponse fresh = MatchingResponse.builder()
            .similarityScore(0.88)
            .compatibilityPercentage(81.0f)
            .compatibilityLevel("high")
            .feedback("Recalculated")
            .usedLlmFeedback(false)
            .build();

        when(vacancyRepository.findAll()).thenReturn(List.of(testVacancy));
        when(profileRepository.findUpdatedAtByUserId(1L)).thenReturn(Optional.of(profileUpdatedAt));
        when(vacancySwipeRepository.findSwipedVacancyIdsByUserId(1L)).thenReturn(List.of());
        when(recommendationCacheRepository.findByUserIdAndVacancyId(1L, 1L)).thenReturn(Optional.of(cache));
        when(matchingService.calculateMatch(1L, 1L)).thenReturn(fresh);

        List<VacancyRecommendationResponse> recommendations = vacancyService.getRecommendedVacancies(1L, 0.0f, 10);

        assertEquals(1, recommendations.size());
        assertEquals(81.0f, recommendations.get(0).getCompatibilityPercentage());
        verify(matchingService, times(1)).calculateMatch(1L, 1L);
        verify(recommendationCacheRepository, times(1)).save(any(RecommendationCache.class));
        }

        @Test
        void shouldRecalculateWhenCacheSourceTimestampsDoNotMatch() {
        LocalDateTime profileUpdatedAt = LocalDateTime.now();
        LocalDateTime staleProfileUpdatedAt = profileUpdatedAt.minusDays(1);
        testVacancy.setCreatedAt(LocalDateTime.now().minusMinutes(2));

        RecommendationCache cache = RecommendationCache.builder()
            .userId(1L)
            .vacancyId(1L)
            .sourceProfileUpdatedAt(staleProfileUpdatedAt)
            .sourceVacancyUpdatedAt(testVacancy.getCreatedAt())
            .updatedAt(Instant.now())
            .build();

        MatchingResponse fresh = MatchingResponse.builder()
            .similarityScore(0.67)
            .compatibilityPercentage(70.0f)
            .compatibilityLevel("medium")
            .feedback("Updated profile")
            .usedLlmFeedback(true)
            .build();

        when(vacancyRepository.findAll()).thenReturn(List.of(testVacancy));
        when(profileRepository.findUpdatedAtByUserId(1L)).thenReturn(Optional.of(profileUpdatedAt));
        when(vacancySwipeRepository.findSwipedVacancyIdsByUserId(1L)).thenReturn(List.of());
        when(recommendationCacheRepository.findByUserIdAndVacancyId(1L, 1L)).thenReturn(Optional.of(cache));
        when(matchingService.calculateMatch(1L, 1L)).thenReturn(fresh);

        List<VacancyRecommendationResponse> recommendations = vacancyService.getRecommendedVacancies(1L, 0.0f, 10);

        assertFalse(recommendations.isEmpty());
        assertEquals(70.0f, recommendations.get(0).getCompatibilityPercentage());
        verify(matchingService, times(1)).calculateMatch(1L, 1L);
        }

        @Test
        void shouldRecalculateWhenCacheSourceVacancyTimestampDoesNotMatch() {
        LocalDateTime profileUpdatedAt = LocalDateTime.now();
        LocalDateTime vacancyUpdatedAt = LocalDateTime.now().minusMinutes(1);
        testVacancy.setUpdatedAt(vacancyUpdatedAt);

        RecommendationCache cache = RecommendationCache.builder()
            .userId(1L)
            .vacancyId(1L)
            .sourceProfileUpdatedAt(profileUpdatedAt)
            .sourceVacancyUpdatedAt(vacancyUpdatedAt.minusHours(2))
            .updatedAt(Instant.now())
            .build();

        when(vacancyRepository.findAll()).thenReturn(List.of(testVacancy));
        when(profileRepository.findUpdatedAtByUserId(1L)).thenReturn(Optional.of(profileUpdatedAt));
        when(vacancySwipeRepository.findSwipedVacancyIdsByUserId(1L)).thenReturn(List.of());
        when(recommendationCacheRepository.findByUserIdAndVacancyId(1L, 1L)).thenReturn(Optional.of(cache));
        when(matchingService.calculateMatch(1L, 1L)).thenReturn(MatchingResponse.builder()
            .similarityScore(0.82)
            .compatibilityPercentage(82.0f)
            .compatibilityLevel("high")
            .feedback("Vacancy changed")
            .build());

        List<VacancyRecommendationResponse> recommendations = vacancyService.getRecommendedVacancies(1L, 0.0f, 10);

        assertEquals(1, recommendations.size());
        verify(matchingService, times(1)).calculateMatch(1L, 1L);
        }

        @Test
        void shouldRecalculateWhenCacheIsOlderThanThreshold() {
        LocalDateTime profileUpdatedAt = LocalDateTime.now();
        testVacancy.setCreatedAt(LocalDateTime.now().minusMinutes(5));

        RecommendationCache cache = RecommendationCache.builder()
            .userId(1L)
            .vacancyId(1L)
            .sourceProfileUpdatedAt(profileUpdatedAt)
            .sourceVacancyUpdatedAt(testVacancy.getCreatedAt())
            .updatedAt(Instant.now().minusSeconds(7200))
            .build();

        when(vacancyRepository.findAll()).thenReturn(List.of(testVacancy));
        when(profileRepository.findUpdatedAtByUserId(1L)).thenReturn(Optional.of(profileUpdatedAt));
        when(vacancySwipeRepository.findSwipedVacancyIdsByUserId(1L)).thenReturn(List.of());
        when(recommendationCacheRepository.findByUserIdAndVacancyId(1L, 1L)).thenReturn(Optional.of(cache));
        when(matchingService.calculateMatch(1L, 1L)).thenReturn(MatchingResponse.builder()
            .similarityScore(0.77)
            .compatibilityPercentage(77.0f)
            .compatibilityLevel("medium")
            .feedback("Cache expired")
            .build());

        List<VacancyRecommendationResponse> recommendations = vacancyService.getRecommendedVacancies(1L, 0.0f, 10);

        assertEquals(1, recommendations.size());
        verify(matchingService, times(1)).calculateMatch(1L, 1L);
        }

        @Test
        void shouldReportProgressAndUseUpdatedAtWhenPresent() {
        LocalDateTime profileUpdatedAt = LocalDateTime.now();
        LocalDateTime vacancyUpdatedAt = LocalDateTime.now().minusMinutes(1);
        testVacancy.setUpdatedAt(vacancyUpdatedAt);
        testVacancy.setCreatedAt(vacancyUpdatedAt.minusDays(1));

        List<String> progressMessages = new ArrayList<>();

        when(vacancyRepository.findAll()).thenReturn(List.of(testVacancy));
        when(profileRepository.findUpdatedAtByUserId(1L)).thenReturn(Optional.of(profileUpdatedAt));
        when(vacancySwipeRepository.findSwipedVacancyIdsByUserId(1L)).thenReturn(List.of());
        when(recommendationCacheRepository.findByUserIdAndVacancyId(1L, 1L)).thenReturn(Optional.empty());
        when(matchingService.calculateMatch(1L, 1L)).thenReturn(MatchingResponse.builder()
            .similarityScore(0.9)
            .compatibilityPercentage(90.0f)
            .compatibilityLevel("high")
            .feedback("Great fit")
            .build());

        List<VacancyRecommendationResponse> recommendations = vacancyService.getRecommendedVacanciesWithProgress(
            1L,
            0.0f,
            10,
            (processed, total, message) -> progressMessages.add(processed + "/" + total + ":" + message));

        assertEquals(1, recommendations.size());
        assertEquals(2, progressMessages.size());
        assertTrue(progressMessages.get(0).startsWith("0/1:"));
        assertTrue(progressMessages.get(1).startsWith("1/1:"));
        }

        @Test
        void shouldSkipSwipedVacanciesWhenBuildingRecommendations() {
        when(vacancyRepository.findAll()).thenReturn(List.of(testVacancy));
        when(profileRepository.findUpdatedAtByUserId(1L)).thenReturn(Optional.of(LocalDateTime.now()));
        when(vacancySwipeRepository.findSwipedVacancyIdsByUserId(1L)).thenReturn(List.of(1L));

        List<VacancyRecommendationResponse> recommendations = vacancyService.getRecommendedVacanciesWithProgress(
            1L,
            0.0f,
            10,
            null);

        assertTrue(recommendations.isEmpty());
        verify(matchingService, never()).calculateMatch(anyLong(), anyLong());
        }

        @Test
        void shouldSkipVacancyWhenMatchingThrowsRuntimeException() {
        Vacancy second = Vacancy.builder()
            .id(2L)
            .title("Junior Developer")
            .description("Looking for a junior developer")
            .location("Medellín, Colombia")
            .modality(Modality.HYBRID)
            .employmentType(EmploymentType.PART_TIME)
            .experienceLevel(ExperienceLevel.JUNIOR)
            .company(testCompany)
            .build();

        when(vacancyRepository.findAll()).thenReturn(Arrays.asList(testVacancy, second));
        when(profileRepository.findUpdatedAtByUserId(1L)).thenReturn(Optional.of(LocalDateTime.now()));
        when(vacancySwipeRepository.findSwipedVacancyIdsByUserId(1L)).thenReturn(List.of());
        when(recommendationCacheRepository.findByUserIdAndVacancyId(any(Long.class), any(Long.class)))
            .thenReturn(Optional.empty());
        when(matchingService.calculateMatch(1L, 1L)).thenThrow(new RuntimeException("AI failed"));
        when(matchingService.calculateMatch(1L, 2L)).thenReturn(MatchingResponse.builder()
            .similarityScore(0.72)
            .compatibilityPercentage(72.0f)
            .compatibilityLevel("medium")
            .feedback("Good fit")
            .build());

        List<VacancyRecommendationResponse> recommendations = vacancyService.getRecommendedVacancies(1L, 0.0f, 10);

        assertEquals(1, recommendations.size());
        assertEquals(2L, recommendations.get(0).getVacancyId());
        verify(matchingService, times(1)).calculateMatch(1L, 1L);
        verify(matchingService, times(1)).calculateMatch(1L, 2L);
        }

        @Test
        void shouldMapNullCollectionsToEmptyWhenCreatingVacancy() {
        CreateVacancyRequest requestWithNullLists = new CreateVacancyRequest();
        requestWithNullLists.setTitle("Data Engineer");
        requestWithNullLists.setDescription("Pipelines and cloud");
        requestWithNullLists.setLocation("Bogota");
        requestWithNullLists.setModality("REMOTE");
        requestWithNullLists.setEmploymentType("FULL_TIME");
        requestWithNullLists.setExperienceLevel("SENIOR");
        requestWithNullLists.setMinSalary(6000.0);
        requestWithNullLists.setMaxSalary(9000.0);

        when(userRepository.findById(1L)).thenReturn(Optional.of(testCompany));
        when(vacancyRepository.save(any(Vacancy.class))).thenAnswer(i -> i.getArgument(0));

        VacancyDetailResponse created = vacancyService.createVacancy(requestWithNullLists, 1L);

        assertNotNull(created.technologies());
        assertTrue(created.technologies().isEmpty());
        assertNotNull(created.softSkills());
        assertTrue(created.softSkills().isEmpty());
        assertNotNull(created.responsibilities());
        assertTrue(created.responsibilities().isEmpty());
        assertNotNull(created.technicalRequirements());
        assertTrue(created.technicalRequirements().isEmpty());
        assertNotNull(created.benefits());
        assertTrue(created.benefits().isEmpty());
        }

        @Test
        void shouldMapNullCollectionsToEmptyWhenUpdatingVacancy() {
        CreateVacancyRequest updateRequest = new CreateVacancyRequest();
        updateRequest.setTitle("Lead Developer");
        updateRequest.setDescription("Lead role");
        updateRequest.setLocation("Cali");
        updateRequest.setModality("ON_SITE");
        updateRequest.setEmploymentType("FULL_TIME");
        updateRequest.setExperienceLevel("SENIOR");
        updateRequest.setMinSalary(9000.0);
        updateRequest.setMaxSalary(12000.0);

        when(vacancyRepository.findById(1L)).thenReturn(Optional.of(testVacancy));
        when(vacancyRepository.save(any(Vacancy.class))).thenAnswer(i -> i.getArgument(0));

        VacancyDetailResponse updated = vacancyService.updateVacancy(1L, updateRequest);

        assertNotNull(updated.technologies());
        assertTrue(updated.technologies().isEmpty());
        assertNotNull(updated.benefits());
        assertTrue(updated.benefits().isEmpty());
        verify(vacancyRepository, never()).delete(any(Vacancy.class));
        }

        @Test
        void shouldKeepNonNullCollectionsWhenCreatingVacancy() {
        CreateVacancyRequest requestWithLists = new CreateVacancyRequest();
        requestWithLists.setTitle("Platform Engineer");
        requestWithLists.setDescription("Build platform tools");
        requestWithLists.setLocation("Bogota");
        requestWithLists.setModality("REMOTE");
        requestWithLists.setEmploymentType("FULL_TIME");
        requestWithLists.setExperienceLevel("SEMI_SENIOR");
        requestWithLists.setMinSalary(6500.0);
        requestWithLists.setMaxSalary(9500.0);
        requestWithLists.setTechnologies(Arrays.asList("Java", "Spring"));

        when(userRepository.findById(1L)).thenReturn(Optional.of(testCompany));
        when(vacancyRepository.save(any(Vacancy.class))).thenAnswer(i -> i.getArgument(0));

        VacancyDetailResponse created = vacancyService.createVacancy(requestWithLists, 1L);

        assertNotNull(created.technologies());
        assertEquals(2, created.technologies().size());
        assertEquals("Java", created.technologies().get(0));
        }

        @Test
        void shouldRegisterCompanyDecisionLikeAndDetectMatch() {
        User company = User.builder().id(1L).role(Role.COMPANY).build();
        User candidate = User.builder().id(2L).role(Role.CANDIDATE).build();
        Vacancy vacancy = Vacancy.builder().id(3L).company(company).build();

        when(userRepository.findById(1L)).thenReturn(Optional.of(company));
        when(userRepository.findById(2L)).thenReturn(Optional.of(candidate));
        when(vacancyRepository.findById(3L)).thenReturn(Optional.of(vacancy));
        when(companyCandidateDecisionRepository.findByCompanyIdAndCandidateIdAndVacancyId(1L, 2L, 3L))
            .thenReturn(Optional.empty());
        when(vacancySwipeRepository.existsByUserIdAndVacancyIdAndDecision(2L, 3L, SwipeDecisionType.LIKE))
            .thenReturn(true);

        CompanyCandidateDecisionResponse response = registerCompanyCandidateDecision(
            1L,
            3L,
            2L,
            SwipeDecisionType.LIKE,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null);

        assertEquals(SwipeDecisionType.LIKE, response.getDecision());
        assertTrue(response.isMatched());
        assertTrue(response.getRejectionTags().isEmpty());
        assertTrue(response.getMissingTechnologies().isEmpty());
        verify(recommendationCacheRepository, never()).findByUserIdAndVacancyId(anyLong(), anyLong());
        }

        @Test
        void shouldRegisterCompanyDecisionLikeWithoutMatchWhenCandidateDidNotLike() {
        User company = User.builder().id(1L).role(Role.COMPANY).build();
        User candidate = User.builder().id(2L).role(Role.CANDIDATE).build();
        Vacancy vacancy = Vacancy.builder().id(3L).company(company).build();

        when(userRepository.findById(1L)).thenReturn(Optional.of(company));
        when(userRepository.findById(2L)).thenReturn(Optional.of(candidate));
        when(vacancyRepository.findById(3L)).thenReturn(Optional.of(vacancy));
        when(companyCandidateDecisionRepository.findByCompanyIdAndCandidateIdAndVacancyId(1L, 2L, 3L))
            .thenReturn(Optional.empty());
        when(vacancySwipeRepository.existsByUserIdAndVacancyIdAndDecision(2L, 3L, SwipeDecisionType.LIKE))
            .thenReturn(false);

        CompanyCandidateDecisionResponse response = registerCompanyCandidateDecision(
            1L,
            3L,
            2L,
            SwipeDecisionType.LIKE,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null);

        assertFalse(response.isMatched());
        }

        @Test
        void shouldRegisterCompanyDecisionDislikeAndUseFallbackAiSummary() {
        User company = User.builder().id(1L).role(Role.COMPANY).build();
        User candidate = User.builder().id(2L).role(Role.CANDIDATE).build();
        Vacancy vacancy = Vacancy.builder().id(3L).company(company).build();

        when(userRepository.findById(1L)).thenReturn(Optional.of(company));
        when(userRepository.findById(2L)).thenReturn(Optional.of(candidate));
        when(vacancyRepository.findById(3L)).thenReturn(Optional.of(vacancy));
        when(companyCandidateDecisionRepository.findByCompanyIdAndCandidateIdAndVacancyId(1L, 2L, 3L))
            .thenReturn(Optional.empty());
        when(recommendationCacheRepository.findByUserIdAndVacancyId(2L, 3L))
            .thenReturn(Optional.of(RecommendationCache.builder().feedback("From recommendation cache").build()));
        when(vacancySwipeRepository.existsByUserIdAndVacancyIdAndDecision(2L, 3L, SwipeDecisionType.LIKE))
            .thenReturn(false);

        CompanyCandidateDecisionResponse response = registerCompanyCandidateDecision(
            1L,
            3L,
            2L,
            SwipeDecisionType.DISLIKE,
            "  Missing seniority  ",
            List.of("java", " ", "java", "spring"),
            List.of("kafka", "kafka", " "),
            List.of("ownership"),
            List.of("system design"),
            "  SENIOR  ",
            "   ",
            "  Needs stronger architecture background  ");

        assertEquals(SwipeDecisionType.DISLIKE, response.getDecision());
        assertFalse(response.isMatched());
        assertEquals("Missing seniority", response.getRejectionReason());
        assertEquals(List.of("java", "spring"), response.getRejectionTags());
        assertEquals(List.of("kafka"), response.getMissingTechnologies());
        assertEquals("SENIOR", response.getExpectedExperienceLevel());
        assertEquals("From recommendation cache", response.getAiSummary());
        assertEquals("Needs stronger architecture background", response.getRejectionComment());
        }

        @Test
        void shouldKeepProvidedAiSummaryOnDislikeDecision() {
        User company = User.builder().id(1L).role(Role.COMPANY).build();
        User candidate = User.builder().id(2L).role(Role.CANDIDATE).build();
        Vacancy vacancy = Vacancy.builder().id(3L).company(company).build();

        when(userRepository.findById(1L)).thenReturn(Optional.of(company));
        when(userRepository.findById(2L)).thenReturn(Optional.of(candidate));
        when(vacancyRepository.findById(3L)).thenReturn(Optional.of(vacancy));
        when(companyCandidateDecisionRepository.findByCompanyIdAndCandidateIdAndVacancyId(1L, 2L, 3L))
            .thenReturn(Optional.empty());
        when(vacancySwipeRepository.existsByUserIdAndVacancyIdAndDecision(2L, 3L, SwipeDecisionType.LIKE))
            .thenReturn(false);

        CompanyCandidateDecisionResponse response = registerCompanyCandidateDecision(
            1L,
            3L,
            2L,
            SwipeDecisionType.DISLIKE,
            "Reason",
            null,
            null,
            null,
            null,
            null,
            "Model-generated summary",
            null);

        assertEquals("Model-generated summary", response.getAiSummary());
        }

        @Test
        void shouldSetNullAiSummaryWhenDislikeHasNoProvidedOrCachedSummary() {
        User company = User.builder().id(1L).role(Role.COMPANY).build();
        User candidate = User.builder().id(2L).role(Role.CANDIDATE).build();
        Vacancy vacancy = Vacancy.builder().id(3L).company(company).build();

        when(userRepository.findById(1L)).thenReturn(Optional.of(company));
        when(userRepository.findById(2L)).thenReturn(Optional.of(candidate));
        when(vacancyRepository.findById(3L)).thenReturn(Optional.of(vacancy));
        when(companyCandidateDecisionRepository.findByCompanyIdAndCandidateIdAndVacancyId(1L, 2L, 3L))
            .thenReturn(Optional.empty());
        when(recommendationCacheRepository.findByUserIdAndVacancyId(2L, 3L)).thenReturn(Optional.empty());
        when(vacancySwipeRepository.existsByUserIdAndVacancyIdAndDecision(2L, 3L, SwipeDecisionType.LIKE))
            .thenReturn(false);

        CompanyCandidateDecisionResponse response = registerCompanyCandidateDecision(
            1L,
            3L,
            2L,
            SwipeDecisionType.DISLIKE,
            "Reason",
            null,
            null,
            null,
            null,
            null,
            " ",
            null);

        assertEquals(null, response.getAiSummary());
        }

        @Test
        void shouldClearRejectionMetadataWhenDecisionChangesToLike() {
        User company = User.builder().id(1L).role(Role.COMPANY).build();
        User candidate = User.builder().id(2L).role(Role.CANDIDATE).build();
        Vacancy vacancy = Vacancy.builder().id(3L).company(company).build();
        CompanyCandidateDecision existing = CompanyCandidateDecision.builder()
            .companyId(1L)
            .candidateId(2L)
            .vacancyId(3L)
            .decision(SwipeDecisionType.DISLIKE)
            .rejectionReason("old")
            .aiSummary("old-summary")
            .build();

        when(userRepository.findById(1L)).thenReturn(Optional.of(company));
        when(userRepository.findById(2L)).thenReturn(Optional.of(candidate));
        when(vacancyRepository.findById(3L)).thenReturn(Optional.of(vacancy));
        when(companyCandidateDecisionRepository.findByCompanyIdAndCandidateIdAndVacancyId(1L, 2L, 3L))
            .thenReturn(Optional.of(existing));
        when(vacancySwipeRepository.existsByUserIdAndVacancyIdAndDecision(2L, 3L, SwipeDecisionType.LIKE))
            .thenReturn(true);

        CompanyCandidateDecisionResponse response = registerCompanyCandidateDecision(
            1L,
            3L,
            2L,
            SwipeDecisionType.LIKE,
            "ignored",
            List.of("ignored"),
            List.of("ignored"),
            List.of("ignored"),
            List.of("ignored"),
            "ignored",
            "ignored",
            "ignored");

        assertTrue(response.isMatched());
        assertEquals(List.of(), response.getRejectionTags());
        assertEquals(List.of(), response.getMissingResponsibilities());
        }

        @Test
        void shouldThrowWhenCompanyDecisionUserIsNotCompany() {
        User notCompany = User.builder().id(1L).role(Role.CANDIDATE).build();
        when(userRepository.findById(1L)).thenReturn(Optional.of(notCompany));

        assertThrows(IllegalArgumentException.class, () -> registerCompanyCandidateDecision(
            1L, 3L, 2L, SwipeDecisionType.LIKE, null, null, null, null, null, null, null, null));
        }

        @Test
        void shouldThrowWhenCompanyDecisionCompanyUserNotFound() {
        when(userRepository.findById(1L)).thenReturn(Optional.empty());

        assertThrows(RuntimeException.class, () -> registerCompanyCandidateDecision(
            1L, 3L, 2L, SwipeDecisionType.LIKE, null, null, null, null, null, null, null, null));
        }

        @Test
        void shouldThrowWhenCompanyDecisionTargetIsNotCandidate() {
        User company = User.builder().id(1L).role(Role.COMPANY).build();
        User notCandidate = User.builder().id(2L).role(Role.COMPANY).build();
        when(userRepository.findById(1L)).thenReturn(Optional.of(company));
        when(userRepository.findById(2L)).thenReturn(Optional.of(notCandidate));

        assertThrows(IllegalArgumentException.class, () -> registerCompanyCandidateDecision(
            1L, 3L, 2L, SwipeDecisionType.LIKE, null, null, null, null, null, null, null, null));
        }

        @Test
        void shouldThrowWhenCompanyDecisionCandidateUserNotFound() {
        User company = User.builder().id(1L).role(Role.COMPANY).build();
        when(userRepository.findById(1L)).thenReturn(Optional.of(company));
        when(userRepository.findById(2L)).thenReturn(Optional.empty());

        assertThrows(RuntimeException.class, () -> registerCompanyCandidateDecision(
            1L, 3L, 2L, SwipeDecisionType.LIKE, null, null, null, null, null, null, null, null));
        }

        @Test
        void shouldThrowWhenCompanyDecisionVacancyDoesNotBelongToCompany() {
        User company = User.builder().id(1L).role(Role.COMPANY).build();
        User candidate = User.builder().id(2L).role(Role.CANDIDATE).build();
        User otherCompany = User.builder().id(8L).role(Role.COMPANY).build();
        Vacancy vacancy = Vacancy.builder().id(3L).company(otherCompany).build();

        when(userRepository.findById(1L)).thenReturn(Optional.of(company));
        when(userRepository.findById(2L)).thenReturn(Optional.of(candidate));
        when(vacancyRepository.findById(3L)).thenReturn(Optional.of(vacancy));

        assertThrows(RuntimeException.class, () -> registerCompanyCandidateDecision(
            1L, 3L, 2L, SwipeDecisionType.LIKE, null, null, null, null, null, null, null, null));
        }

        @Test
        void shouldThrowWhenCompanyDecisionVacancyHasNullCompany() {
        User company = User.builder().id(1L).role(Role.COMPANY).build();
        User candidate = User.builder().id(2L).role(Role.CANDIDATE).build();
        Vacancy vacancy = Vacancy.builder().id(3L).company(null).build();

        when(userRepository.findById(1L)).thenReturn(Optional.of(company));
        when(userRepository.findById(2L)).thenReturn(Optional.of(candidate));
        when(vacancyRepository.findById(3L)).thenReturn(Optional.of(vacancy));

        assertThrows(RuntimeException.class, () -> registerCompanyCandidateDecision(
            1L, 3L, 2L, SwipeDecisionType.LIKE, null, null, null, null, null, null, null, null));
        }

        @Test
        void shouldThrowWhenCompanyDecisionVacancyNotFound() {
        User company = User.builder().id(1L).role(Role.COMPANY).build();
        User candidate = User.builder().id(2L).role(Role.CANDIDATE).build();

        when(userRepository.findById(1L)).thenReturn(Optional.of(company));
        when(userRepository.findById(2L)).thenReturn(Optional.of(candidate));
        when(vacancyRepository.findById(3L)).thenReturn(Optional.empty());

        assertThrows(RuntimeException.class, () -> registerCompanyCandidateDecision(
            1L, 3L, 2L, SwipeDecisionType.LIKE, null, null, null, null, null, null, null, null));
        }

        @Test
        void shouldGetCandidateApplicationsWithMappedFieldsAndDefaults() {
        User candidate = User.builder().id(2L).role(Role.CANDIDATE).build();
        when(userRepository.findById(2L)).thenReturn(Optional.of(candidate));
        when(vacancySwipeRepository.findCandidateApplications(2L, 30)).thenReturn(List.of(
            candidateApplicationProjection(
                10L,
                "Backend",
                1L,
                "Tech Co",
                LocalDateTime.now(),
                "DISLIKE",
                LocalDateTime.now(),
                Boolean.FALSE,
                "Reason",
                "tag1,tag2",
                "tech1",
                "resp1",
                "req1",
                "SENIOR",
                "Summary",
                "Comment"),
            candidateApplicationProjection(
                11L,
                null,
                2L,
                null,
                LocalDateTime.now(),
                " ",
                null,
                Boolean.TRUE,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null)));

        List<CandidateApplicationResponse> result = vacancyService.getCandidateApplications(2L, null);

        assertEquals(2, result.size());
        assertEquals(SwipeDecisionType.DISLIKE, result.get(0).getDecision());
        assertEquals(List.of("tag1", "tag2"), result.get(0).getRejectionTags());
        assertEquals("Vacante", result.get(1).getVacancyTitle());
        assertEquals("Empresa", result.get(1).getCompanyName());
        assertEquals(null, result.get(1).getDecision());
        }

        @Test
        void shouldReturnEmptyCandidateApplicationsWhenNoRows() {
        User candidate = User.builder().id(2L).role(Role.CANDIDATE).build();
        when(userRepository.findById(2L)).thenReturn(Optional.of(candidate));
        when(vacancySwipeRepository.findCandidateApplications(2L, 30)).thenReturn(List.of());

        List<CandidateApplicationResponse> result = vacancyService.getCandidateApplications(2L, 30);

        assertTrue(result.isEmpty());
        }

        @Test
        void shouldThrowWhenCandidateApplicationsRequestedByNonCandidate() {
        User company = User.builder().id(1L).role(Role.COMPANY).build();
        when(userRepository.findById(1L)).thenReturn(Optional.of(company));

        assertThrows(IllegalArgumentException.class, () -> vacancyService.getCandidateApplications(1L, 30));
        }

        @Test
        void shouldThrowWhenCandidateApplicationsUserNotFound() {
        when(userRepository.findById(404L)).thenReturn(Optional.empty());

        assertThrows(RuntimeException.class, () -> vacancyService.getCandidateApplications(404L, 30));
        }

        @Test
        void shouldGetMatchesForCandidateRole() {
        User candidate = User.builder().id(2L).role(Role.CANDIDATE).build();
        when(userRepository.findById(2L)).thenReturn(Optional.of(candidate));
        when(companyCandidateDecisionRepository.findCandidateMatches(2L, 20)).thenReturn(List.of(
            userMatchProjection(10L, null, 1L, null, LocalDateTime.now(), 88.0f, "high")));

        List<UserMatchResponse> matches = vacancyService.getMatchesForUser(2L, 20);

        assertEquals(1, matches.size());
        assertEquals("Vacante", matches.get(0).getVacancyTitle());
        assertEquals("Usuario", matches.get(0).getCounterpartName());
        }

        @Test
        void shouldGetMatchesForCompanyRole() {
        User company = User.builder().id(1L).role(Role.COMPANY).build();
        when(userRepository.findById(1L)).thenReturn(Optional.of(company));
        when(companyCandidateDecisionRepository.findCompanyMatches(1L, 100)).thenReturn(List.of(
            userMatchProjection(10L, "Backend", 2L, "Ana", LocalDateTime.now(), 91.0f, "high")));

        List<UserMatchResponse> matches = vacancyService.getMatchesForUser(1L, 999);

        assertEquals(1, matches.size());
        assertEquals("Ana", matches.get(0).getCounterpartName());
        }

        @Test
        void shouldReturnEmptyMatchesWhenNoRows() {
        User company = User.builder().id(1L).role(Role.COMPANY).build();
        when(userRepository.findById(1L)).thenReturn(Optional.of(company));
        when(companyCandidateDecisionRepository.findCompanyMatches(1L, 20)).thenReturn(List.of());

        List<UserMatchResponse> matches = vacancyService.getMatchesForUser(1L, 20);

        assertTrue(matches.isEmpty());
        }

        @Test
        void shouldThrowWhenMatchesUserNotFound() {
        when(userRepository.findById(404L)).thenReturn(Optional.empty());

        assertThrows(RuntimeException.class, () -> vacancyService.getMatchesForUser(404L, 20));
        }

        @Test
        void shouldUseDefaultLimitWhenGettingMatches() {
        User candidate = User.builder().id(2L).role(Role.CANDIDATE).build();
        when(userRepository.findById(2L)).thenReturn(Optional.of(candidate));
        when(companyCandidateDecisionRepository.findCandidateMatches(2L, 20)).thenReturn(List.of());

        List<UserMatchResponse> matches = vacancyService.getMatchesForUser(2L, null);

        assertTrue(matches.isEmpty());
        verify(companyCandidateDecisionRepository, times(1)).findCandidateMatches(2L, 20);
        }

        @Test
        void shouldParseMidExperienceLevelWhenCreatingVacancy() {
        testRequest.setExperienceLevel("MID");
        when(userRepository.findById(1L)).thenReturn(Optional.of(testCompany));
        when(vacancyRepository.save(any(Vacancy.class))).thenAnswer(invocation -> invocation.getArgument(0));

        VacancyDetailResponse created = vacancyService.createVacancy(testRequest, 1L);

        assertEquals("MID", created.experienceLevel());
        }

        @Test
        void shouldParseSemiSeniorWithHyphenWhenCreatingVacancy() {
        testRequest.setExperienceLevel("semi-senior");
        when(userRepository.findById(1L)).thenReturn(Optional.of(testCompany));
        when(vacancyRepository.save(any(Vacancy.class))).thenAnswer(invocation -> invocation.getArgument(0));

        VacancyDetailResponse created = vacancyService.createVacancy(testRequest, 1L);

        assertEquals("SEMI_SENIOR", created.experienceLevel());
        }

        @Test
        void shouldThrowWhenExperienceLevelMissing() {
        testRequest.setExperienceLevel(null);
        when(userRepository.findById(1L)).thenReturn(Optional.of(testCompany));

        assertThrows(IllegalArgumentException.class, () -> vacancyService.createVacancy(testRequest, 1L));
        }

        @Test
        void shouldReturnOptimizedNotSwipedVacanciesWhenAvailable() {
        User candidate = User.builder().id(1L).role(Role.CANDIDATE).build();
        Vacancy optimized = Vacancy.builder().id(2L).title("Optimized").company(testCompany).build();

        when(userRepository.findById(1L)).thenReturn(Optional.of(candidate));
        when(vacancySwipeRepository.findSwipedVacancyIdsByUserId(1L)).thenReturn(List.of(1L));
        when(vacancyRepository.findAllNotSwipedByUser(1L)).thenReturn(List.of(optimized));

        List<Vacancy> result = vacancyService.getAllVacanciesForUser(1L);

        assertEquals(1, result.size());
        assertEquals(2L, result.get(0).getId());
        verify(vacancyRepository, never()).findAll();
        }

        @Test
        void shouldUseLegacyPipelineQueryWhenDecisionRepositoryIsNull() {
        VacancyService serviceWithoutDecisionRepo = new VacancyService(
            vacancyRepository,
            userRepository,
            matchingService,
            profileRepository,
            recommendationCacheRepository,
            vacancySwipeRepository,
            null,
            cacheManager);

        Vacancy first = Vacancy.builder().id(1L).title("First").company(testCompany).build();
        Vacancy second = Vacancy.builder().id(2L).title("Second").company(testCompany).build();
        List<Object[]> grouped = new ArrayList<>();
        grouped.add(new Object[] { 1L, 5L });
        when(vacancyRepository.findAllByCompanyId(1L)).thenReturn(List.of(first, second));
        when(vacancySwipeRepository.countByVacancyIdsAndDecision(List.of(1L, 2L), SwipeDecisionType.LIKE))
            .thenReturn(grouped);

        var pipeline = serviceWithoutDecisionRepo.getCompanyVacancyPipeline(1L);

        assertEquals(2, pipeline.size());
        assertEquals(1L, pipeline.get(0).getVacancyId());
        assertEquals(5, pipeline.get(0).getApplicantsCount());
        }

        @Test
        void shouldExcludeAlreadyDecidedCandidatesInCompanyActivity() {
        Vacancy companyVacancy = Vacancy.builder().id(10L).title("Backend").company(testCompany).build();
        VacancySwipe likeA = VacancySwipe.builder().userId(100L).vacancyId(10L).decision(SwipeDecisionType.LIKE).updatedAt(LocalDateTime.now()).build();
        VacancySwipe likeB = VacancySwipe.builder().userId(101L).vacancyId(10L).decision(SwipeDecisionType.LIKE).updatedAt(LocalDateTime.now()).build();
        CompanyCandidateDecision decisionA = CompanyCandidateDecision.builder().companyId(1L).candidateId(100L).vacancyId(10L).decision(SwipeDecisionType.LIKE).build();

        when(vacancyRepository.findAllByCompanyId(1L)).thenReturn(List.of(companyVacancy));
        when(vacancySwipeRepository.findRecentByVacancyIdsAndDecision(any(), eq(SwipeDecisionType.LIKE), any()))
            .thenReturn(List.of(likeA, likeB));
        when(companyCandidateDecisionRepository.findByCompanyIdAndVacancyIdIn(1L, List.of(10L)))
            .thenReturn(List.of(decisionA));
        when(userRepository.findAllById(any())).thenReturn(List.of(
            User.builder().id(100L).name("Ana").build(),
            User.builder().id(101L).name("Luis").build()));

        var result = vacancyService.getCompanyLikeActivity(1L, 20);

        assertEquals(1, result.size());
        assertEquals(101L, result.get(0).getCandidateId());
        verify(userRepository, never()).findById(101L);
        }

        @Test
        void shouldHandleNullDecisionsListInCompanyActivity() {
        Vacancy companyVacancy = Vacancy.builder().id(10L).title("Backend").company(testCompany).build();
        VacancySwipe like = VacancySwipe.builder().userId(100L).vacancyId(10L).decision(SwipeDecisionType.LIKE).updatedAt(LocalDateTime.now()).build();

        when(vacancyRepository.findAllByCompanyId(1L)).thenReturn(List.of(companyVacancy));
        when(vacancySwipeRepository.findRecentByVacancyIdsAndDecision(any(), eq(SwipeDecisionType.LIKE), any()))
            .thenReturn(List.of(like));
        when(companyCandidateDecisionRepository.findByCompanyIdAndVacancyIdIn(1L, List.of(10L)))
            .thenReturn(null);
        when(userRepository.findAllById(any())).thenReturn(List.of(User.builder().id(100L).name("Ana").build()));

        var result = vacancyService.getCompanyLikeActivity(1L, 20);

        assertEquals(1, result.size());
        assertEquals("Ana", result.get(0).getCandidateName());
        }

        @Test
        void shouldUseLegacyApplicantsFlowWhenDecisionRepositoryIsNull() {
        VacancyService serviceWithoutDecisionRepo = new VacancyService(
            vacancyRepository,
            userRepository,
            matchingService,
            profileRepository,
            recommendationCacheRepository,
            vacancySwipeRepository,
            null,
            cacheManager);

        User company = User.builder().id(1L).role(Role.COMPANY).build();
        Vacancy vacancy = Vacancy.builder().id(88L).title("Backend").company(company).build();
        VacancySwipe like = VacancySwipe.builder().userId(200L).vacancyId(88L).decision(SwipeDecisionType.LIKE).updatedAt(LocalDateTime.now()).build();

        when(vacancyRepository.findById(88L)).thenReturn(Optional.of(vacancy));
        when(vacancySwipeRepository.findByVacancyIdAndDecisionOrderByUpdatedAtDesc(eq(88L), eq(SwipeDecisionType.LIKE), any()))
            .thenReturn(List.of(like));
        when(recommendationCacheRepository.findByVacancyIdAndUserIdIn(eq(88L), any())).thenReturn(List.of());
        when(userRepository.findAllById(any())).thenReturn(List.of(User.builder().id(200L).name("Carla").build()));

        var result = serviceWithoutDecisionRepo.getApplicantsByVacancy(1L, 88L, 10);

        assertEquals(1, result.size());
        assertEquals("Carla", result.get(0).getCandidateName());
        }

        @Test
        void shouldSkipDecidedCandidatesInApplicantsByVacancy() {
        User company = User.builder().id(1L).role(Role.COMPANY).build();
        Vacancy vacancy = Vacancy.builder().id(90L).title("Backend").company(company).build();
        VacancySwipe like = VacancySwipe.builder().userId(300L).vacancyId(90L).decision(SwipeDecisionType.LIKE).updatedAt(LocalDateTime.now()).build();
        CompanyCandidateDecision decision = CompanyCandidateDecision.builder().companyId(1L).candidateId(300L).vacancyId(90L).decision(SwipeDecisionType.DISLIKE).build();

        when(vacancyRepository.findById(90L)).thenReturn(Optional.of(vacancy));
        when(vacancySwipeRepository.findByVacancyIdAndDecisionOrderByUpdatedAtDesc(eq(90L), eq(SwipeDecisionType.LIKE), any()))
            .thenReturn(List.of(like));
        when(companyCandidateDecisionRepository.findByCompanyIdAndVacancyIdAndCandidateIdIn(eq(1L), eq(90L), any()))
            .thenReturn(List.of(decision));

        var result = vacancyService.getApplicantsByVacancy(1L, 90L, 10);

        assertTrue(result.isEmpty());
        }

        @Test
        void shouldHandleNullDecisionsListInApplicantsByVacancy() {
        User company = User.builder().id(1L).role(Role.COMPANY).build();
        Vacancy companyVacancy = Vacancy.builder().id(91L).title("Vacancy").company(company).build();
        VacancySwipe like = VacancySwipe.builder()
            .userId(301L)
            .vacancyId(91L)
            .decision(SwipeDecisionType.LIKE)
            .updatedAt(LocalDateTime.now())
            .build();

        when(vacancyRepository.findById(91L)).thenReturn(Optional.of(companyVacancy));
        when(vacancySwipeRepository.findByVacancyIdAndDecisionOrderByUpdatedAtDesc(eq(91L), eq(SwipeDecisionType.LIKE), any()))
            .thenReturn(List.of(like));
        when(companyCandidateDecisionRepository.findByCompanyIdAndVacancyIdAndCandidateIdIn(eq(1L), eq(91L), any()))
            .thenReturn(null);
        when(recommendationCacheRepository.findByVacancyIdAndUserIdIn(eq(91L), any())).thenReturn(List.of());
        when(userRepository.findAllById(any())).thenReturn(List.of(User.builder().id(301L).name("Nora").build()));

        var result = vacancyService.getApplicantsByVacancy(1L, 91L, 10);

        assertEquals(1, result.size());
        assertEquals("Nora", result.get(0).getCandidateName());
        }

        @Test
        void shouldTruncateRejectionFieldsWhenTooLong() {
        User company = User.builder().id(1L).role(Role.COMPANY).build();
        User candidate = User.builder().id(2L).role(Role.CANDIDATE).build();
        Vacancy vacancy = Vacancy.builder().id(3L).company(company).build();

        String longReason = "x".repeat(200);
        String longTag = "a".repeat(700);
        String longExpectedLevel = "b".repeat(80);
        String longComment = "c".repeat(2000);

        when(userRepository.findById(1L)).thenReturn(Optional.of(company));
        when(userRepository.findById(2L)).thenReturn(Optional.of(candidate));
        when(vacancyRepository.findById(3L)).thenReturn(Optional.of(vacancy));
        when(companyCandidateDecisionRepository.findByCompanyIdAndCandidateIdAndVacancyId(1L, 2L, 3L))
            .thenReturn(Optional.empty());
        when(vacancySwipeRepository.existsByUserIdAndVacancyIdAndDecision(2L, 3L, SwipeDecisionType.LIKE))
            .thenReturn(false);

        CompanyCandidateDecisionResponse response = registerCompanyCandidateDecision(
            1L,
            3L,
            2L,
            SwipeDecisionType.DISLIKE,
            longReason,
            List.of(longTag),
            null,
            null,
            null,
            longExpectedLevel,
            null,
            longComment);

        assertEquals(120, response.getRejectionReason().length());
        assertEquals(600, response.getRejectionTags().get(0).length());
        assertEquals(32, response.getExpectedExperienceLevel().length());
        assertEquals(1200, response.getRejectionComment().length());
        }

        @Test
        void shouldReturnEmptyListsWhenNormalizedCsvBecomesEmpty() {
        User company = User.builder().id(1L).role(Role.COMPANY).build();
        User candidate = User.builder().id(2L).role(Role.CANDIDATE).build();
        Vacancy vacancy = Vacancy.builder().id(3L).company(company).build();

        when(userRepository.findById(1L)).thenReturn(Optional.of(company));
        when(userRepository.findById(2L)).thenReturn(Optional.of(candidate));
        when(vacancyRepository.findById(3L)).thenReturn(Optional.of(vacancy));
        when(companyCandidateDecisionRepository.findByCompanyIdAndCandidateIdAndVacancyId(1L, 2L, 3L))
            .thenReturn(Optional.empty());
        when(vacancySwipeRepository.existsByUserIdAndVacancyIdAndDecision(2L, 3L, SwipeDecisionType.LIKE))
            .thenReturn(false);

        CompanyCandidateDecisionResponse response = registerCompanyCandidateDecision(
            1L,
            3L,
            2L,
            SwipeDecisionType.DISLIKE,
            "Reason",
            List.of("   ", "\t"),
            List.of(" "),
            List.of(" "),
            List.of(" "),
            null,
            null,
            null);

        assertTrue(response.getRejectionTags().isEmpty());
        assertTrue(response.getMissingTechnologies().isEmpty());
        assertTrue(response.getMissingResponsibilities().isEmpty());
        assertTrue(response.getMissingTechnicalRequirements().isEmpty());
        }

        @Test
        void shouldIgnoreControlCharacterAfterTrimInNormalizeCsv() {
        User company = User.builder().id(1L).role(Role.COMPANY).build();
        User candidate = User.builder().id(2L).role(Role.CANDIDATE).build();
        Vacancy vacancy = Vacancy.builder().id(3L).company(company).build();

        when(userRepository.findById(1L)).thenReturn(Optional.of(company));
        when(userRepository.findById(2L)).thenReturn(Optional.of(candidate));
        when(vacancyRepository.findById(3L)).thenReturn(Optional.of(vacancy));
        when(companyCandidateDecisionRepository.findByCompanyIdAndCandidateIdAndVacancyId(1L, 2L, 3L))
            .thenReturn(Optional.empty());
        when(vacancySwipeRepository.existsByUserIdAndVacancyIdAndDecision(2L, 3L, SwipeDecisionType.LIKE))
            .thenReturn(false);

        CompanyCandidateDecisionResponse response = registerCompanyCandidateDecision(
            1L,
            3L,
            2L,
            SwipeDecisionType.DISLIKE,
            "Reason",
            List.of("\u0000", "java"),
            null,
            null,
            null,
            null,
            null,
            null);

        assertEquals(List.of("java"), response.getRejectionTags());
        }

        @Test
        void shouldFilterEmptyCsvFragmentsInCandidateApplications() {
        User candidate = User.builder().id(2L).role(Role.CANDIDATE).build();
        when(userRepository.findById(2L)).thenReturn(Optional.of(candidate));
        when(vacancySwipeRepository.findCandidateApplications(2L, 30)).thenReturn(List.of(
            candidateApplicationProjection(
                10L,
                "Backend",
                1L,
                "Tech Co",
                LocalDateTime.now(),
                "DISLIKE",
                LocalDateTime.now(),
                Boolean.FALSE,
                "Reason",
                "tag1,,tag2, ,",
                ",tech1,,",
                null,
                null,
                null,
                null,
                null)));

        List<CandidateApplicationResponse> result = vacancyService.getCandidateApplications(2L, 30);

        assertEquals(List.of("tag1", "tag2"), result.get(0).getRejectionTags());
        assertEquals(List.of("tech1"), result.get(0).getMissingTechnologies());
        }

        @Test
        void shouldUseOptimizedQueryForRecommendationsWhenSwipesExist() {
        Vacancy second = Vacancy.builder()
            .id(2L)
            .title("Junior")
            .description("desc")
            .location("Bogota")
            .modality(Modality.HYBRID)
            .employmentType(EmploymentType.FULL_TIME)
            .experienceLevel(ExperienceLevel.JUNIOR)
            .company(testCompany)
            .build();

        when(vacancySwipeRepository.findSwipedVacancyIdsByUserId(1L)).thenReturn(List.of(99L));
        when(vacancyRepository.findAllNotSwipedByUser(1L)).thenReturn(List.of(second));
        when(profileRepository.findUpdatedAtByUserId(1L)).thenReturn(Optional.of(LocalDateTime.now()));
        when(recommendationCacheRepository.findByUserIdAndVacancyId(1L, 2L)).thenReturn(Optional.empty());
        when(matchingService.calculateMatch(1L, 2L)).thenReturn(MatchingResponse.builder()
            .similarityScore(0.7)
            .compatibilityPercentage(80.0f)
            .compatibilityLevel("high")
            .feedback("fit")
            .build());

        List<VacancyRecommendationResponse> recommendations = vacancyService.getRecommendedVacanciesWithProgress(
            1L,
            0.0f,
            10,
            null);

        assertEquals(1, recommendations.size());
        verify(vacancyRepository, never()).findAll();
        }

        @Test
        void shouldFallbackToFindAllWhenOptimizedRecommendationsAreEmpty() {
        Vacancy second = Vacancy.builder()
            .id(2L)
            .title("Junior")
            .description("desc")
            .location("Bogota")
            .modality(Modality.HYBRID)
            .employmentType(EmploymentType.FULL_TIME)
            .experienceLevel(ExperienceLevel.JUNIOR)
            .company(testCompany)
            .build();

        when(vacancySwipeRepository.findSwipedVacancyIdsByUserId(1L)).thenReturn(List.of(1L));
        when(vacancyRepository.findAllNotSwipedByUser(1L)).thenReturn(List.of());
        when(vacancyRepository.findAll()).thenReturn(List.of(testVacancy, second));
        when(profileRepository.findUpdatedAtByUserId(1L)).thenReturn(Optional.of(LocalDateTime.now()));
        when(recommendationCacheRepository.findByUserIdAndVacancyId(1L, 2L)).thenReturn(Optional.empty());
        when(matchingService.calculateMatch(1L, 2L)).thenReturn(MatchingResponse.builder()
            .similarityScore(0.8)
            .compatibilityPercentage(81.0f)
            .compatibilityLevel("high")
            .feedback("fit")
            .build());

        List<VacancyRecommendationResponse> recommendations = vacancyService.getRecommendedVacanciesWithProgress(
            1L,
            0.0f,
            10,
            null);

        assertEquals(1, recommendations.size());
        verify(vacancyRepository, times(1)).findAll();
        }

        @Test
        void shouldSkipRecommendationWhenScoreIsBelowMinAndCompatibilityIsNull() {
        when(vacancySwipeRepository.findSwipedVacancyIdsByUserId(1L)).thenReturn(List.of());
        when(vacancyRepository.findAll()).thenReturn(List.of(testVacancy));
        when(profileRepository.findUpdatedAtByUserId(1L)).thenReturn(Optional.of(LocalDateTime.now()));
        when(recommendationCacheRepository.findByUserIdAndVacancyId(1L, 1L)).thenReturn(Optional.empty());
        when(matchingService.calculateMatch(1L, 1L)).thenReturn(MatchingResponse.builder()
            .similarityScore(0.6)
            .compatibilityPercentage(null)
            .compatibilityLevel("unknown")
            .feedback("No score")
            .build());

        List<VacancyRecommendationResponse> recommendations = vacancyService.getRecommendedVacanciesWithProgress(
            1L,
            10.0f,
            10,
            null);

        assertTrue(recommendations.isEmpty());
        }

        @Test
        void shouldSkipRecommendationWhenVacancyWasAlreadySwipedInEvaluation() {
        when(vacancySwipeRepository.findSwipedVacancyIdsByUserId(1L)).thenReturn(List.of(1L));
        // Simulate a stale optimized query returning an already-swiped vacancy so the
        // service executes the in-method swiped filter branch.
        when(vacancyRepository.findAllNotSwipedByUser(1L)).thenReturn(List.of(testVacancy));
        when(profileRepository.findUpdatedAtByUserId(1L)).thenReturn(Optional.of(LocalDateTime.now()));

        List<VacancyRecommendationResponse> recommendations = vacancyService.getRecommendedVacanciesWithProgress(
            1L,
            0.0f,
            10,
            null);

        assertTrue(recommendations.isEmpty());
        verify(matchingService, never()).calculateMatch(anyLong(), anyLong());
        }

        @Test
        void shouldHandleEmptyRejectionTagsListInNormalizeCsv() {
        User company = User.builder().id(1L).role(Role.COMPANY).build();
        User candidate = User.builder().id(2L).role(Role.CANDIDATE).build();
        Vacancy vacancy = Vacancy.builder().id(3L).company(company).build();

        when(userRepository.findById(1L)).thenReturn(Optional.of(company));
        when(userRepository.findById(2L)).thenReturn(Optional.of(candidate));
        when(vacancyRepository.findById(3L)).thenReturn(Optional.of(vacancy));
        when(companyCandidateDecisionRepository.findByCompanyIdAndCandidateIdAndVacancyId(1L, 2L, 3L))
            .thenReturn(Optional.empty());
        when(vacancySwipeRepository.existsByUserIdAndVacancyIdAndDecision(2L, 3L, SwipeDecisionType.LIKE))
            .thenReturn(false);

        CompanyCandidateDecisionResponse response = registerCompanyCandidateDecision(
            1L,
            3L,
            2L,
            SwipeDecisionType.DISLIKE,
            "Reason",
            List.of(),
            null,
            null,
            null,
            null,
            null,
            null);

        assertTrue(response.getRejectionTags().isEmpty());
        }

        private CandidateApplicationProjection candidateApplicationProjection(
            Long vacancyId,
            String vacancyTitle,
            Long companyId,
            String companyName,
            LocalDateTime appliedAt,
            String decision,
            LocalDateTime decisionAt,
            Boolean matched,
            String rejectionReason,
            String rejectionTags,
            String missingTechnologies,
            String missingResponsibilities,
            String missingTechnicalRequirements,
            String expectedExperienceLevel,
            String aiSummary,
            String rejectionComment) {
        return new CandidateApplicationProjection() {
            @Override
            public Long getVacancyId() {
                return vacancyId;
            }

            @Override
            public String getVacancyTitle() {
                return vacancyTitle;
            }

            @Override
            public Long getCompanyId() {
                return companyId;
            }

            @Override
            public String getCompanyName() {
                return companyName;
            }

            @Override
            public LocalDateTime getAppliedAt() {
                return appliedAt;
            }

            @Override
            public String getDecision() {
                return decision;
            }

            @Override
            public LocalDateTime getDecisionAt() {
                return decisionAt;
            }

            @Override
            public Boolean getMatched() {
                return matched;
            }

            @Override
            public String getRejectionReason() {
                return rejectionReason;
            }

            @Override
            public String getRejectionTags() {
                return rejectionTags;
            }

            @Override
            public String getMissingTechnologies() {
                return missingTechnologies;
            }

            @Override
            public String getMissingResponsibilities() {
                return missingResponsibilities;
            }

            @Override
            public String getMissingTechnicalRequirements() {
                return missingTechnicalRequirements;
            }

            @Override
            public String getExpectedExperienceLevel() {
                return expectedExperienceLevel;
            }

            @Override
            public String getAiSummary() {
                return aiSummary;
            }

            @Override
            public String getRejectionComment() {
                return rejectionComment;
            }
        };
        }

        private UserMatchProjection userMatchProjection(
            Long vacancyId,
            String vacancyTitle,
            Long counterpartId,
            String counterpartName,
            LocalDateTime matchedAt,
            Float compatibilityPercentage,
            String compatibilityLevel) {
        return new UserMatchProjection() {
            @Override
            public Long getVacancyId() {
                return vacancyId;
            }

            @Override
            public String getVacancyTitle() {
                return vacancyTitle;
            }

            @Override
            public Long getCounterpartId() {
                return counterpartId;
            }

            @Override
            public String getCounterpartName() {
                return counterpartName;
            }

            @Override
            public LocalDateTime getMatchedAt() {
                return matchedAt;
            }

            @Override
            public Float getCompatibilityPercentage() {
                return compatibilityPercentage;
            }

            @Override
            public String getCompatibilityLevel() {
                return compatibilityLevel;
            }
        };
        }
}

