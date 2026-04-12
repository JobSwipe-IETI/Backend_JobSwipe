package ieti.jobswipe.controller;

import ieti.jobswipe.controller.VacancyController;
import ieti.jobswipe.controller.VacancyController;
import ieti.jobswipe.dto.CreateVacancyRequest;
import ieti.jobswipe.dto.VacancyRecommendationResponse;
import ieti.jobswipe.model.EmploymentType;
import ieti.jobswipe.model.ExperienceLevel;
import ieti.jobswipe.model.Modality;
import ieti.jobswipe.model.Role;
import ieti.jobswipe.model.User;
import ieti.jobswipe.model.Vacancy;
import ieti.jobswipe.service.VacancyService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.oauth2.jwt.Jwt;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.anyFloat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class VacancyControllerTest {

    @Mock
    private VacancyService vacancyService;

    @InjectMocks
    private VacancyController vacancyController;

    private User testCompany;
    private Vacancy testVacancy;
    private Vacancy testVacancy2;
    private CreateVacancyRequest testRequest;

    @BeforeEach
    void setUp() {
        testCompany = User.builder()
                .id(1L)
                .name("Tech Company Inc")
                .email("company@tech.com")
                .role(Role.COMPANY)
                .build();

        testVacancy = Vacancy.builder()
                .id(1L)
                .title("Backend Developer")
                .description("Java + Spring Boot")
                .location("Bogota, Colombia")
                .modality(Modality.REMOTE)
                .employmentType(EmploymentType.FULL_TIME)
                .experienceLevel(ExperienceLevel.SENIOR)
                .minSalary(5000.0)
                .maxSalary(8000.0)
                .company(testCompany)
                .build();

        testVacancy2 = Vacancy.builder()
                .id(2L)
                .title("Frontend Developer")
                .description("React + TypeScript")
                .location("Medellin, Colombia")
                .modality(Modality.HYBRID)
                .employmentType(EmploymentType.FULL_TIME)
                .experienceLevel(ExperienceLevel.SEMI_SENIOR)
                .minSalary(4000.0)
                .maxSalary(6000.0)
                .company(testCompany)
                .build();

        testRequest = new CreateVacancyRequest();
        testRequest.setTitle("Backend Developer");
        testRequest.setDescription("Java + Spring Boot");
        testRequest.setLocation("Bogota, Colombia");
        testRequest.setModality("REMOTE");
        testRequest.setEmploymentType("FULL_TIME");
        testRequest.setExperienceLevel("SENIOR");
        testRequest.setMinSalary(5000.0);
        testRequest.setMaxSalary(8000.0);
    }

    @Test
    void shouldGetAllVacancies() {
        List<Vacancy> vacancies = Arrays.asList(testVacancy, testVacancy2);
        when(vacancyService.getAllVacancies()).thenReturn(vacancies);

        ResponseEntity<List<Vacancy>> response = vacancyController.getAllVacancies();

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(2, response.getBody().size());
        assertEquals("Backend Developer", response.getBody().get(0).getTitle());
        assertEquals("Frontend Developer", response.getBody().get(1).getTitle());
        verify(vacancyService, times(1)).getAllVacancies();
    }

    @Test
    void shouldGetRecommendedVacancies() {
        List<VacancyRecommendationResponse> recommendations = Arrays.asList(
                VacancyRecommendationResponse.builder()
                        .vacancyId(1L)
                        .title("Backend Developer")
                        .location("Bogota, Colombia")
                        .compatibilityPercentage(88.0f)
                        .compatibilityLevel("high")
                        .similarityScore(0.91)
                        .feedback("Great fit")
                        .build(),
                VacancyRecommendationResponse.builder()
                        .vacancyId(2L)
                        .title("Frontend Developer")
                        .location("Medellin, Colombia")
                        .compatibilityPercentage(72.0f)
                        .compatibilityLevel("medium")
                        .similarityScore(0.74)
                        .feedback("Good fit")
                        .build());

        when(vacancyService.getRecommendedVacancies(1L, 70.0f, 10)).thenReturn(recommendations);

        ResponseEntity<List<VacancyRecommendationResponse>> response = vacancyController.getRecommendedVacancies(
                jwt("1", "CANDIDATE"), 70.0f, 10);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(2, response.getBody().size());
        assertEquals(88.0f, response.getBody().get(0).getCompatibilityPercentage());
        verify(vacancyService, times(1)).getRecommendedVacancies(1L, 70.0f, 10);
    }

    @Test
    void shouldReturn400WhenRecommendedParamsAreInvalid() {
        ResponseEntity<List<VacancyRecommendationResponse>> response = vacancyController.getRecommendedVacancies(
                jwt("1", "CANDIDATE"), -1.0f, 10);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertNull(response.getBody());
        verify(vacancyService, times(0)).getRecommendedVacancies(anyLong(), anyFloat(), anyInt());
    }

    @Test
    void shouldReturn400WhenRecommendedMinScoreExceedsMaximum() {
        ResponseEntity<List<VacancyRecommendationResponse>> response = vacancyController.getRecommendedVacancies(
                jwt("1", "CANDIDATE"), 101.0f, 10);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertNull(response.getBody());
        verify(vacancyService, times(0)).getRecommendedVacancies(anyLong(), anyFloat(), anyInt());
    }

    @Test
    void shouldReturn400WhenRecommendedLimitIsNotPositive() {
        ResponseEntity<List<VacancyRecommendationResponse>> response = vacancyController.getRecommendedVacancies(
                jwt("1", "CANDIDATE"), 50.0f, 0);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertNull(response.getBody());
        verify(vacancyService, times(0)).getRecommendedVacancies(anyLong(), anyFloat(), anyInt());
    }

    @Test
    void shouldGetVacancyById() {
        when(vacancyService.getVacancyById(1L)).thenReturn(testVacancy);

        ResponseEntity<Vacancy> response = vacancyController.getVacancyById(1L);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(1L, response.getBody().getId());
        assertEquals("Backend Developer", response.getBody().getTitle());
        verify(vacancyService, times(1)).getVacancyById(1L);
    }

    @Test
    void shouldReturn404WhenVacancyNotFound() {
        when(vacancyService.getVacancyById(999L)).thenThrow(new RuntimeException("Vacancy not found"));

        ResponseEntity<Vacancy> response = vacancyController.getVacancyById(999L);

        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
        assertNull(response.getBody());
        verify(vacancyService, times(1)).getVacancyById(999L);
    }

    @Test
    void shouldCreateVacancy() {
        when(vacancyService.createVacancy(any(CreateVacancyRequest.class), eq(1L))).thenReturn(testVacancy);

        ResponseEntity<Vacancy> response = vacancyController.createVacancy(testRequest, jwt("1", "COMPANY"));

        assertEquals(HttpStatus.CREATED, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(1L, response.getBody().getId());
        assertEquals("Backend Developer", response.getBody().getTitle());
        verify(vacancyService, times(1)).createVacancy(any(CreateVacancyRequest.class), eq(1L));
    }

    @Test
    void shouldReturn404WhenCompanyNotFound() {
        when(vacancyService.createVacancy(any(CreateVacancyRequest.class), eq(999L)))
                .thenThrow(new RuntimeException("Company not found"));

        ResponseEntity<Vacancy> response = vacancyController.createVacancy(testRequest, jwt("999", "COMPANY"));

        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
        assertNull(response.getBody());
        verify(vacancyService, times(1)).createVacancy(any(CreateVacancyRequest.class), eq(999L));
    }

    @Test
    void shouldReturn400WhenRoleIsNotCompany() {
        when(vacancyService.createVacancy(any(CreateVacancyRequest.class), eq(1L)))
                .thenThrow(new IllegalArgumentException("Only COMPANY users can create vacancies"));

        ResponseEntity<Vacancy> response = vacancyController.createVacancy(testRequest, jwt("1", "CANDIDATE"));

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertNull(response.getBody());
        verify(vacancyService, times(1)).createVacancy(any(CreateVacancyRequest.class), eq(1L));
    }

    @Test
    void shouldUpdateVacancy() {
        CreateVacancyRequest updateRequest = new CreateVacancyRequest();
        updateRequest.setTitle("Senior Backend Developer");
        updateRequest.setDescription("Java + Spring Boot + Kubernetes");
        updateRequest.setLocation("Bogota, Colombia");
        updateRequest.setModality("REMOTE");
        updateRequest.setEmploymentType("FULL_TIME");
        updateRequest.setExperienceLevel("SENIOR");
        updateRequest.setMinSalary(6000.0);
        updateRequest.setMaxSalary(9000.0);

        Vacancy updatedVacancy = Vacancy.builder()
                .id(1L)
                .title("Senior Backend Developer")
                .description("Java + Spring Boot + Kubernetes")
                .location("Bogota, Colombia")
                .modality(Modality.REMOTE)
                .employmentType(EmploymentType.FULL_TIME)
                .experienceLevel(ExperienceLevel.SENIOR)
                .minSalary(6000.0)
                .maxSalary(9000.0)
                .company(testCompany)
                .build();

        when(vacancyService.updateVacancy(eq(1L), any(CreateVacancyRequest.class))).thenReturn(updatedVacancy);

        ResponseEntity<Vacancy> response = vacancyController.updateVacancy(1L, updateRequest);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals("Senior Backend Developer", response.getBody().getTitle());
        verify(vacancyService, times(1)).updateVacancy(eq(1L), any(CreateVacancyRequest.class));
    }

    @Test
    void shouldReturn400WhenUpdatingVacancyWithInvalidData() {
        when(vacancyService.updateVacancy(eq(1L), any(CreateVacancyRequest.class)))
                .thenThrow(new IllegalArgumentException("Invalid salary range"));

        ResponseEntity<Vacancy> response = vacancyController.updateVacancy(1L, testRequest);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertNull(response.getBody());
        verify(vacancyService, times(1)).updateVacancy(eq(1L), any(CreateVacancyRequest.class));
    }

    @Test
    void shouldReturn404WhenUpdatingVacancyNotFound() {
        when(vacancyService.updateVacancy(eq(999L), any(CreateVacancyRequest.class)))
                .thenThrow(new RuntimeException("Vacancy not found"));

        ResponseEntity<Vacancy> response = vacancyController.updateVacancy(999L, testRequest);

        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
        assertNull(response.getBody());
        verify(vacancyService, times(1)).updateVacancy(eq(999L), any(CreateVacancyRequest.class));
    }

    @Test
    void shouldDeleteVacancy() {
        doNothing().when(vacancyService).deleteVacancy(1L);

        ResponseEntity<Void> response = vacancyController.deleteVacancy(1L);

        assertEquals(HttpStatus.NO_CONTENT, response.getStatusCode());
        verify(vacancyService, times(1)).deleteVacancy(1L);
    }

    @Test
    void shouldReturn404WhenDeletingVacancyNotFound() {
        doThrow(new RuntimeException("Vacancy not found")).when(vacancyService).deleteVacancy(999L);

        ResponseEntity<Void> response = vacancyController.deleteVacancy(999L);

        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
        verify(vacancyService, times(1)).deleteVacancy(999L);
    }

    private Jwt jwt(String subject, String role) {
        return Jwt.withTokenValue("test-token")
                .header("alg", "HS256")
                .subject(subject)
                .claim("role", role)
                .build();
    }
}

