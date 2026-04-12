package ieti.jobswipe.controller;

import ieti.jobswipe.dto.CreateVacancyRequest;
import ieti.jobswipe.dto.VacancyRecommendationResponse;
import ieti.jobswipe.model.EmploymentType;
import ieti.jobswipe.model.ExperienceLevel;
import ieti.jobswipe.model.Modality;
import ieti.jobswipe.model.Role;
import ieti.jobswipe.model.SwipeDecisionType;
import ieti.jobswipe.model.User;
import ieti.jobswipe.model.Vacancy;
import ieti.jobswipe.service.RecommendationJobService;
import ieti.jobswipe.service.VacancyService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.oauth2.jwt.Jwt;

import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
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

    @Mock
    private RecommendationJobService recommendationJobService;

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
        when(vacancyService.getAllVacanciesForUser(1L)).thenReturn(vacancies);

        ResponseEntity<List<Vacancy>> response = vacancyController.getAllVacancies(jwt("1", "CANDIDATE"));

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(2, response.getBody().size());
        assertEquals("Backend Developer", response.getBody().get(0).getTitle());
        assertEquals("Frontend Developer", response.getBody().get(1).getTitle());
        verify(vacancyService, times(1)).getAllVacanciesForUser(1L);
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
        void shouldRegisterSwipeDecision() {
        ResponseEntity<Void> response = vacancyController.registerSwipeDecision(
            jwt("1", "CANDIDATE"), 99L, SwipeDecisionType.LIKE);

        assertEquals(HttpStatus.NO_CONTENT, response.getStatusCode());
        verify(vacancyService, times(1)).registerSwipeDecision(1L, 99L, SwipeDecisionType.LIKE);
        }

        @Test
        void shouldStartRecommendationJob() {
        when(recommendationJobService.startJob(1L, 20.0f, 5)).thenReturn("job-123");

        ResponseEntity<Map<String, String>> response = vacancyController.startRecommendedVacanciesJob(
            jwt("1", "CANDIDATE"), 20.0f, 5);

        assertEquals(HttpStatus.ACCEPTED, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals("job-123", response.getBody().get("jobId"));
        verify(recommendationJobService, times(1)).startJob(1L, 20.0f, 5);
        }

        @Test
        void shouldReturn400WhenStartRecommendationJobParamsAreInvalid() {
        ResponseEntity<Map<String, String>> response = vacancyController.startRecommendedVacanciesJob(
            jwt("1", "CANDIDATE"), 150.0f, 5);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        verify(recommendationJobService, times(0)).startJob(anyLong(), anyFloat(), anyInt());
        }

            @Test
            void shouldReturn400WhenStartRecommendationJobLimitIsNotPositive() {
            ResponseEntity<Map<String, String>> response = vacancyController.startRecommendedVacanciesJob(
                jwt("1", "CANDIDATE"), 10.0f, 0);

            assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
            verify(recommendationJobService, times(0)).startJob(anyLong(), anyFloat(), anyInt());
            }

            @Test
            void shouldReturn400WhenStartRecommendationJobMinScoreIsNegative() {
            ResponseEntity<Map<String, String>> response = vacancyController.startRecommendedVacanciesJob(
                jwt("1", "CANDIDATE"), -0.1f, 10);

            assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
            verify(recommendationJobService, times(0)).startJob(anyLong(), anyFloat(), anyInt());
            }

            @Test
            void shouldThrowWhenStartRecommendationJobMinScoreIsNull() {
            assertThrows(NullPointerException.class,
                () -> vacancyController.startRecommendedVacanciesJob(jwt("1", "CANDIDATE"), null, 10));
            }

            @Test
            void shouldThrowWhenStartRecommendationJobLimitIsNull() {
            assertThrows(NullPointerException.class,
                () -> vacancyController.startRecommendedVacanciesJob(jwt("1", "CANDIDATE"), 10.0f, null));
            }

        @Test
        void shouldReturnNotFoundWhenJobStatusIsMissing() {
        when(recommendationJobService.getJob("missing", 1L)).thenReturn(Optional.empty());

        ResponseEntity<Map<String, Object>> response = vacancyController.getRecommendedVacanciesJobStatus(
            jwt("1", "CANDIDATE"), "missing");

        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
        }

        @Test
        void shouldReturnRecommendationJobStatus() {
        RecommendationJobService.RecommendationJob job =
            new RecommendationJobService.RecommendationJob("job-1", 1L);
        when(recommendationJobService.getJob("job-1", 1L)).thenReturn(Optional.of(job));

        ResponseEntity<Map<String, Object>> response = vacancyController.getRecommendedVacanciesJobStatus(
            jwt("1", "CANDIDATE"), "job-1");

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals("job-1", response.getBody().get("jobId"));
        assertEquals("RUNNING", response.getBody().get("status"));
        }

        @Test
        void shouldReturnEmptyMessageAndErrorWhenJobStatusFieldsAreNull() {
        RecommendationJobService.RecommendationJob job =
            org.mockito.Mockito.mock(RecommendationJobService.RecommendationJob.class);
        when(job.getJobId()).thenReturn("job-null-fields");
        when(job.getStatus()).thenReturn(RecommendationJobService.JobStatus.RUNNING);
        when(job.getProcessed()).thenReturn(1);
        when(job.getTotal()).thenReturn(5);
        when(job.getProgressPercent()).thenReturn(20);
        when(job.getMessage()).thenReturn(null);
        when(job.getError()).thenReturn(null);
        when(job.getStartedAt()).thenReturn(Instant.now());
        when(recommendationJobService.getJob("job-null-fields", 1L)).thenReturn(Optional.of(job));

        ResponseEntity<Map<String, Object>> response = vacancyController.getRecommendedVacanciesJobStatus(
            jwt("1", "CANDIDATE"), "job-null-fields");

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals("", response.getBody().get("message"));
        assertEquals("", response.getBody().get("error"));
        }

        @Test
        void shouldReturnJobStatusWithNonNullError() {
        RecommendationJobService.RecommendationJob job =
            org.mockito.Mockito.mock(RecommendationJobService.RecommendationJob.class);
        when(job.getJobId()).thenReturn("job-status-failed");
        when(job.getStatus()).thenReturn(RecommendationJobService.JobStatus.FAILED);
        when(job.getProcessed()).thenReturn(5);
        when(job.getTotal()).thenReturn(5);
        when(job.getProgressPercent()).thenReturn(100);
        when(job.getMessage()).thenReturn("No se pudieron generar recomendaciones");
        when(job.getError()).thenReturn("AI timeout");
        when(job.getStartedAt()).thenReturn(Instant.now());
        when(recommendationJobService.getJob("job-status-failed", 1L)).thenReturn(Optional.of(job));

        ResponseEntity<Map<String, Object>> response = vacancyController.getRecommendedVacanciesJobStatus(
            jwt("1", "CANDIDATE"), "job-status-failed");

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals("AI timeout", response.getBody().get("error"));
        }

        @Test
        void shouldReturnAcceptedWhenJobResultIsStillRunning() {
        RecommendationJobService.RecommendationJob runningJob =
            new RecommendationJobService.RecommendationJob("job-running", 1L);
        when(recommendationJobService.getJob("job-running", 1L)).thenReturn(Optional.of(runningJob));

        ResponseEntity<?> response = vacancyController.getRecommendedVacanciesJobResult(
            jwt("1", "CANDIDATE"), "job-running");

        assertEquals(HttpStatus.ACCEPTED, response.getStatusCode());
        assertInstanceOf(Map.class, response.getBody());
        }

        @Test
        void shouldReturnBadGatewayWhenJobResultFailed() {
        RecommendationJobService.RecommendationJob failedJob =
            org.mockito.Mockito.mock(RecommendationJobService.RecommendationJob.class);
        when(failedJob.getStatus()).thenReturn(RecommendationJobService.JobStatus.FAILED);
        when(failedJob.getError()).thenReturn("AI timeout");
        when(recommendationJobService.getJob("job-failed", 1L)).thenReturn(Optional.of(failedJob));

        ResponseEntity<?> response = vacancyController.getRecommendedVacanciesJobResult(
            jwt("1", "CANDIDATE"), "job-failed");

        assertEquals(HttpStatus.BAD_GATEWAY, response.getStatusCode());
        assertInstanceOf(Map.class, response.getBody());
        }

        @Test
        void shouldReturnDefaultFailedMessageWhenJobErrorIsNull() {
        RecommendationJobService.RecommendationJob failedJob =
            org.mockito.Mockito.mock(RecommendationJobService.RecommendationJob.class);
        when(failedJob.getStatus()).thenReturn(RecommendationJobService.JobStatus.FAILED);
        when(failedJob.getError()).thenReturn(null);
        when(recommendationJobService.getJob("job-failed-null", 1L)).thenReturn(Optional.of(failedJob));

        ResponseEntity<?> response = vacancyController.getRecommendedVacanciesJobResult(
            jwt("1", "CANDIDATE"), "job-failed-null");

        assertEquals(HttpStatus.BAD_GATEWAY, response.getStatusCode());
        assertInstanceOf(Map.class, response.getBody());
        assertEquals("Job failed", ((Map<?, ?>) response.getBody()).get("message"));
        }

        @Test
        void shouldReturnResultWhenJobCompleted() {
        RecommendationJobService.RecommendationJob completedJob =
            org.mockito.Mockito.mock(RecommendationJobService.RecommendationJob.class);
        List<VacancyRecommendationResponse> expected = List.of(
            VacancyRecommendationResponse.builder().vacancyId(1L).title("Backend").build());

        when(completedJob.getStatus()).thenReturn(RecommendationJobService.JobStatus.COMPLETED);
        when(completedJob.getResult()).thenReturn(expected);
        when(recommendationJobService.getJob("job-completed", 1L)).thenReturn(Optional.of(completedJob));

        ResponseEntity<?> response = vacancyController.getRecommendedVacanciesJobResult(
            jwt("1", "CANDIDATE"), "job-completed");

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(expected, response.getBody());
        }

        @Test
        void shouldReturnNotFoundWhenJobResultIsMissing() {
        when(recommendationJobService.getJob("missing", 1L)).thenReturn(Optional.empty());

        ResponseEntity<?> response = vacancyController.getRecommendedVacanciesJobResult(
            jwt("1", "CANDIDATE"), "missing");

        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
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

