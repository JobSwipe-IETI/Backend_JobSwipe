package ieti.JobSwipe.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import ieti.JobSwipe.dto.CreateVacancyRequest;
import ieti.JobSwipe.dto.MatchingResponse;
import ieti.JobSwipe.dto.VacancyRecommendationResponse;
import ieti.JobSwipe.model.EmploymentType;
import ieti.JobSwipe.model.ExperienceLevel;
import ieti.JobSwipe.model.Modality;
import ieti.JobSwipe.model.Role;
import ieti.JobSwipe.model.User;
import ieti.JobSwipe.model.Vacancy;
import ieti.JobSwipe.repository.UserRepository;
import ieti.JobSwipe.repository.VacancyRepository;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class VacancyServiceTest {

    @Mock
    private VacancyRepository vacancyRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private MatchingService matchingService;

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
}
