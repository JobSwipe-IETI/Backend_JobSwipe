package ieti.jobswipe.service;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import static org.mockito.ArgumentMatchers.any;
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
import ieti.jobswipe.model.Role;
import ieti.jobswipe.model.User;
import ieti.jobswipe.model.Vacancy;
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

