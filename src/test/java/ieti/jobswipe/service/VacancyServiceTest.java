package ieti.jobswipe.service;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
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
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import org.mockito.junit.jupiter.MockitoExtension;

import ieti.jobswipe.dto.CreateVacancyRequest;
import ieti.jobswipe.dto.MatchingResponse;
import ieti.jobswipe.dto.VacancyRecommendationResponse;
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

@ExtendWith(MockitoExtension.class)
class VacancyServiceTest {

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
    }

    @Test
    void shouldCreateVacancySuccessfully() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(testCompany));
        when(vacancyRepository.save(any(Vacancy.class))).thenReturn(testVacancy);

        Vacancy created = vacancyService.createVacancy(testRequest, 1L);

        assertNotNull(created);
        assertEquals("Senior Developer", created.getTitle());
        assertEquals(1L, created.getCompany().getId());
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

        Vacancy found = vacancyService.getVacancyById(1L);

        assertNotNull(found);
        assertEquals("Senior Developer", found.getTitle());
        assertEquals(1L, found.getId());
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
        when(userRepository.findById(100L)).thenReturn(Optional.of(User.builder().id(100L).name("Ana").build()));
        when(userRepository.findById(101L)).thenReturn(Optional.empty());

        var activity = vacancyService.getCompanyLikeActivity(1L, 20);

        assertEquals(2, activity.size());
        assertEquals("Ana", activity.get(0).getCandidateName());
        assertEquals("Usuario 101", activity.get(1).getCandidateName());
        assertEquals("Backend Developer", activity.get(0).getVacancyTitle());
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
        when(vacancySwipeRepository.countByVacancyIdAndDecision(1L, SwipeDecisionType.LIKE)).thenReturn(1L);
        when(vacancySwipeRepository.countByVacancyIdAndDecision(2L, SwipeDecisionType.LIKE)).thenReturn(3L);

        var pipeline = vacancyService.getCompanyVacancyPipeline(1L);

        assertEquals(2, pipeline.size());
        assertEquals(2L, pipeline.get(0).getVacancyId());
        assertEquals(3, pipeline.get(0).getApplicantsCount());
        assertEquals(1L, pipeline.get(1).getVacancyId());
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
        when(userRepository.findById(200L)).thenReturn(Optional.of(User.builder().id(200L).name("Carlos").build()));

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
        when(userRepository.findById(300L)).thenReturn(Optional.empty());

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

        Vacancy updated = vacancyService.updateVacancy(1L, updateRequest);

        assertNotNull(updated);
        assertEquals("Lead Developer", updated.getTitle());
        assertEquals("Lead role", updated.getDescription());
        assertEquals(9000.0, updated.getMinSalary());
        assertEquals(12000.0, updated.getMaxSalary());
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
        verify(matchingService, times(1)).calculateMatch(eq(1L), eq(1L));
        verify(matchingService, times(1)).calculateMatch(eq(1L), eq(2L));
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

        Vacancy created = vacancyService.createVacancy(requestWithNullLists, 1L);

        assertNotNull(created.getTechnologies());
        assertTrue(created.getTechnologies().isEmpty());
        assertNotNull(created.getSoftSkills());
        assertTrue(created.getSoftSkills().isEmpty());
        assertNotNull(created.getResponsibilities());
        assertTrue(created.getResponsibilities().isEmpty());
        assertNotNull(created.getTechnicalRequirements());
        assertTrue(created.getTechnicalRequirements().isEmpty());
        assertNotNull(created.getBenefits());
        assertTrue(created.getBenefits().isEmpty());
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

        Vacancy updated = vacancyService.updateVacancy(1L, updateRequest);

        assertNotNull(updated.getTechnologies());
        assertTrue(updated.getTechnologies().isEmpty());
        assertNotNull(updated.getBenefits());
        assertTrue(updated.getBenefits().isEmpty());
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

        Vacancy created = vacancyService.createVacancy(requestWithLists, 1L);

        assertNotNull(created.getTechnologies());
        assertEquals(2, created.getTechnologies().size());
        assertEquals("Java", created.getTechnologies().get(0));
        }
}

