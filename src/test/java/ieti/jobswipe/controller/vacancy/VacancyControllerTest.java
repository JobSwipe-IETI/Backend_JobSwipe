package ieti.jobswipe.controller.vacancy;

import ieti.jobswipe.dto.company.CompanyCandidateDecisionRequest;
import ieti.jobswipe.dto.company.CompanyCandidateDecisionResponse;
import ieti.jobswipe.dto.company.CompanyLikeActivityResponse;
import ieti.jobswipe.dto.company.CompanyVacancyPipelineResponse;
import ieti.jobswipe.dto.matching.UserMatchResponse;
import ieti.jobswipe.dto.user.CandidateApplicationResponse;
import ieti.jobswipe.dto.vacancy.CreateVacancyRequest;
import ieti.jobswipe.dto.vacancy.VacancyApplicantResponse;
import ieti.jobswipe.dto.vacancy.VacancyDetailResponse;
import ieti.jobswipe.dto.vacancy.VacancyRecommendationResponse;
import ieti.jobswipe.dto.vacancy.VacancySummaryResponse;
import ieti.jobswipe.controller.vacancy.VacancyController;
import ieti.jobswipe.model.EmploymentType;
import ieti.jobswipe.model.ExperienceLevel;
import ieti.jobswipe.model.Modality;
import ieti.jobswipe.model.Role;
import ieti.jobswipe.model.SwipeDecisionType;
import ieti.jobswipe.model.entity.User;
import ieti.jobswipe.model.entity.Vacancy;
import ieti.jobswipe.service.recommendation.RecommendationJobService;
import ieti.jobswipe.service.vacancy.VacancyService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
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
        List<VacancySummaryResponse> vacancies = Arrays.asList(
                new VacancySummaryResponse(1L, "Backend Developer", "Tech Company Inc", "Bogota, Colombia", "Java + Spring Boot", 5000.0, 8000.0),
                new VacancySummaryResponse(2L, "Frontend Developer", "Tech Company Inc", "Medellin, Colombia", "React + TypeScript", 4000.0, 6000.0));
        when(vacancyService.getVacancySummariesForUser(1L)).thenReturn(vacancies);

        ResponseEntity<List<VacancySummaryResponse>> response = vacancyController.getAllVacancies(jwt("1", "CANDIDATE"));

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(2, response.getBody().size());
        assertEquals("Backend Developer", response.getBody().get(0).title());
        assertEquals("Frontend Developer", response.getBody().get(1).title());
        verify(vacancyService, times(1)).getVacancySummariesForUser(1L);
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
        void shouldGetCompanyActivity() {
        List<CompanyLikeActivityResponse> activity = List.of(
            CompanyLikeActivityResponse.builder()
                .vacancyId(10L)
                .vacancyTitle("Backend")
                .candidateId(20L)
                .candidateName("Ana")
                .likedAt(java.time.LocalDateTime.now())
                .build());
        when(vacancyService.getCompanyLikeActivity(1L, 20)).thenReturn(activity);

        ResponseEntity<List<CompanyLikeActivityResponse>> response =
            vacancyController.getCompanyActivity(jwt("1", "COMPANY"), 20);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(1, response.getBody().size());
        assertEquals("Ana", response.getBody().get(0).getCandidateName());
        }

        @Test
        void shouldReturn400WhenCompanyActivityLimitIsInvalid() {
        ResponseEntity<List<CompanyLikeActivityResponse>> response =
            vacancyController.getCompanyActivity(jwt("1", "COMPANY"), 0);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        verify(vacancyService, times(0)).getCompanyLikeActivity(anyLong(), anyInt());
        }

        @Test
        void shouldReturn400WhenCompanyActivityLimitExceedsMaximum() {
        ResponseEntity<List<CompanyLikeActivityResponse>> response =
            vacancyController.getCompanyActivity(jwt("1", "COMPANY"), 101);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        verify(vacancyService, times(0)).getCompanyLikeActivity(anyLong(), anyInt());
        }

        @Test
        void shouldGetCompanyVacancyPipeline() {
        List<CompanyVacancyPipelineResponse> pipeline = List.of(
            CompanyVacancyPipelineResponse.builder()
                .vacancyId(10L)
                .vacancyTitle("Backend")
                .applicantsCount(3)
                .build());
        when(vacancyService.getCompanyVacancyPipeline(1L)).thenReturn(pipeline);

        ResponseEntity<List<CompanyVacancyPipelineResponse>> response =
            vacancyController.getCompanyVacancyPipeline(jwt("1", "COMPANY"));

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(1, response.getBody().size());
        assertEquals(3, response.getBody().get(0).getApplicantsCount());
        }

        @Test
        void shouldGetApplicantsByVacancy() {
        List<VacancyApplicantResponse> applicants = List.of(
            VacancyApplicantResponse.builder()
                .candidateId(30L)
                .candidateName("Carlos")
                .compatibilityPercentage(85.0f)
                .compatibilityLevel("high")
                .feedback("Great fit")
                .appliedAt(java.time.LocalDateTime.now())
                .build());
        when(vacancyService.getApplicantsByVacancy(1L, 99L, 50)).thenReturn(applicants);

        ResponseEntity<List<VacancyApplicantResponse>> response =
            vacancyController.getApplicantsByVacancy(jwt("1", "COMPANY"), 99L, 50);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(1, response.getBody().size());
        assertEquals("Carlos", response.getBody().get(0).getCandidateName());
        }

        @Test
        void shouldReturn400WhenApplicantsLimitIsInvalid() {
        ResponseEntity<List<VacancyApplicantResponse>> response =
            vacancyController.getApplicantsByVacancy(jwt("1", "COMPANY"), 99L, 0);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        verify(vacancyService, times(0)).getApplicantsByVacancy(anyLong(), anyLong(), anyInt());
        }

        @Test
        void shouldReturn400WhenApplicantsLimitExceedsMaximum() {
        ResponseEntity<List<VacancyApplicantResponse>> response =
            vacancyController.getApplicantsByVacancy(jwt("1", "COMPANY"), 99L, 201);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        verify(vacancyService, times(0)).getApplicantsByVacancy(anyLong(), anyLong(), anyInt());
        }

        @Test
        void shouldReturn404WhenApplicantsServiceThrows() {
        when(vacancyService.getApplicantsByVacancy(1L, 99L, 50)).thenThrow(new RuntimeException("not found"));

        ResponseEntity<List<VacancyApplicantResponse>> response =
            vacancyController.getApplicantsByVacancy(jwt("1", "COMPANY"), 99L, 50);

        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
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
            var candidateJwt = jwt("1", "CANDIDATE");
            assertThrows(NullPointerException.class,
                () -> vacancyController.startRecommendedVacanciesJob(candidateJwt, null, 10));
            }

            @Test
            void shouldThrowWhenStartRecommendationJobLimitIsNull() {
            var candidateJwt = jwt("1", "CANDIDATE");
            assertThrows(NullPointerException.class,
                () -> vacancyController.startRecommendedVacanciesJob(candidateJwt, 10.0f, null));
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
        void shouldReturnBadRequestWhenPartialParamsAreInvalid() {
        ResponseEntity<Object> response = vacancyController.getRecommendedVacanciesJobPartial(
            jwt("1", "CANDIDATE"), "job-1", -1, 10);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        }

        @Test
        void shouldReturnBadRequestWhenPartialLimitIsNotPositive() {
        ResponseEntity<Object> response = vacancyController.getRecommendedVacanciesJobPartial(
            jwt("1", "CANDIDATE"), "job-1", 0, 0);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        }

        @Test
        void shouldReturnBadRequestWhenPartialLimitExceedsMaximum() {
        ResponseEntity<Object> response = vacancyController.getRecommendedVacanciesJobPartial(
            jwt("1", "CANDIDATE"), "job-1", 0, 101);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        }

        @Test
        void shouldReturnNotFoundWhenPartialJobDoesNotExist() {
        when(recommendationJobService.getJob("missing", 1L)).thenReturn(Optional.empty());

        ResponseEntity<Object> response = vacancyController.getRecommendedVacanciesJobPartial(
            jwt("1", "CANDIDATE"), "missing", 0, 10);

        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
        }

        @Test
        void shouldReturnRecommendationPartialResult() {
        RecommendationJobService.RecommendationJob job =
            org.mockito.Mockito.mock(RecommendationJobService.RecommendationJob.class);
        List<VacancyRecommendationResponse> allItems = List.of(
            VacancyRecommendationResponse.builder().vacancyId(1L).title("One").build(),
            VacancyRecommendationResponse.builder().vacancyId(2L).title("Two").build());

        when(job.getJobId()).thenReturn("job-1");
        when(job.getStatus()).thenReturn(RecommendationJobService.JobStatus.COMPLETED);
        when(job.getProcessed()).thenReturn(2);
        when(job.getTotal()).thenReturn(2);
        when(job.getProgressPercent()).thenReturn(100);
        when(job.getMessage()).thenReturn("done");
        when(job.getError()).thenReturn(null);
        when(job.getResult()).thenReturn(allItems);
        when(recommendationJobService.getJob("job-1", 1L)).thenReturn(Optional.of(job));

        ResponseEntity<Object> response = vacancyController.getRecommendedVacanciesJobPartial(
            jwt("1", "CANDIDATE"), "job-1", 1, 10);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertInstanceOf(Map.class, response.getBody());
        Map<?, ?> payload = (Map<?, ?>) response.getBody();
        assertEquals(1, payload.get("offset"));
        assertEquals(2, payload.get("nextOffset"));
        assertEquals(true, payload.get("done"));
        assertEquals(1, ((List<?>) payload.get("items")).size());
        }

        @Test
        void shouldReturnRecommendationPartialResultWhenOffsetExceedsResultSize() {
        RecommendationJobService.RecommendationJob job =
            org.mockito.Mockito.mock(RecommendationJobService.RecommendationJob.class);
        List<VacancyRecommendationResponse> allItems = List.of(
            VacancyRecommendationResponse.builder().vacancyId(1L).title("One").build());

        when(job.getJobId()).thenReturn("job-big-offset");
        when(job.getStatus()).thenReturn(RecommendationJobService.JobStatus.RUNNING);
        when(job.getProcessed()).thenReturn(1);
        when(job.getTotal()).thenReturn(2);
        when(job.getProgressPercent()).thenReturn(50);
        when(job.getMessage()).thenReturn(null);
        when(job.getError()).thenReturn(null);
        when(job.getResult()).thenReturn(allItems);
        when(recommendationJobService.getJob("job-big-offset", 1L)).thenReturn(Optional.of(job));

        ResponseEntity<Object> response = vacancyController.getRecommendedVacanciesJobPartial(
            jwt("1", "CANDIDATE"), "job-big-offset", 50, 10);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertInstanceOf(Map.class, response.getBody());
        Map<?, ?> payload = (Map<?, ?>) response.getBody();
        assertEquals(1, payload.get("offset"));
        assertEquals(1, payload.get("nextOffset"));
        assertEquals(false, payload.get("done"));
        assertEquals("", payload.get("message"));
        assertEquals("", payload.get("error"));
        assertEquals(0, ((List<?>) payload.get("items")).size());
        }

        @Test
        void shouldIncludeErrorWhenPartialJobHasError() {
        RecommendationJobService.RecommendationJob job =
            org.mockito.Mockito.mock(RecommendationJobService.RecommendationJob.class);

        when(job.getJobId()).thenReturn("job-error");
        when(job.getStatus()).thenReturn(RecommendationJobService.JobStatus.FAILED);
        when(job.getProcessed()).thenReturn(1);
        when(job.getTotal()).thenReturn(1);
        when(job.getProgressPercent()).thenReturn(100);
        when(job.getMessage()).thenReturn("failed");
        when(job.getError()).thenReturn("AI timeout");
        when(job.getResult()).thenReturn(List.of());
        when(recommendationJobService.getJob("job-error", 1L)).thenReturn(Optional.of(job));

        ResponseEntity<Object> response = vacancyController.getRecommendedVacanciesJobPartial(
            jwt("1", "CANDIDATE"), "job-error", 0, 10);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertInstanceOf(Map.class, response.getBody());
        Map<?, ?> payload = (Map<?, ?>) response.getBody();
        assertEquals("AI timeout", payload.get("error"));
        }

    @Test
    void shouldGetVacancyById() {
        when(vacancyService.getVacancyById(1L)).thenReturn(new VacancyDetailResponse(
                1L,
                "Backend Developer",
                "Java + Spring Boot",
                "Bogota, Colombia",
                null,
                "REMOTE",
                "FULL_TIME",
                "SENIOR",
                List.of("Java"),
                List.of("Teamwork"),
                List.of("Build APIs"),
                List.of("Spring Boot"),
                5000.0,
                8000.0,
                List.of("Remote"),
                1L,
                "Tech Company Inc"));

        ResponseEntity<VacancyDetailResponse> response = vacancyController.getVacancyById(1L);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(1L, response.getBody().id());
        assertEquals("Backend Developer", response.getBody().title());
        verify(vacancyService, times(1)).getVacancyById(1L);
    }

    @Test
    void shouldReturn404WhenVacancyNotFound() {
        when(vacancyService.getVacancyById(999L)).thenThrow(new RuntimeException("Vacancy not found"));

        ResponseEntity<VacancyDetailResponse> response = vacancyController.getVacancyById(999L);

        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
        assertNull(response.getBody());
        verify(vacancyService, times(1)).getVacancyById(999L);
    }

    @Test
    void shouldCreateVacancy() {
        when(vacancyService.createVacancy(any(CreateVacancyRequest.class), eq(1L))).thenReturn(new VacancyDetailResponse(
                1L,
                "Backend Developer",
                "Java + Spring Boot",
                "Bogota, Colombia",
                null,
                "REMOTE",
                "FULL_TIME",
                "SENIOR",
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                5000.0,
                8000.0,
                List.of(),
                1L,
                "Tech Company Inc"));

        ResponseEntity<VacancyDetailResponse> response = vacancyController.createVacancy(testRequest, jwt("1", "COMPANY"));

        assertEquals(HttpStatus.CREATED, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(1L, response.getBody().id());
        assertEquals("Backend Developer", response.getBody().title());
        verify(vacancyService, times(1)).createVacancy(any(CreateVacancyRequest.class), eq(1L));
    }

    @Test
    void shouldReturn404WhenCompanyNotFound() {
        when(vacancyService.createVacancy(any(CreateVacancyRequest.class), eq(999L)))
                .thenThrow(new RuntimeException("Company not found"));

        ResponseEntity<VacancyDetailResponse> response = vacancyController.createVacancy(testRequest, jwt("999", "COMPANY"));

        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
        assertNull(response.getBody());
        verify(vacancyService, times(1)).createVacancy(any(CreateVacancyRequest.class), eq(999L));
    }

    @Test
    void shouldReturn400WhenRoleIsNotCompany() {
        when(vacancyService.createVacancy(any(CreateVacancyRequest.class), eq(1L)))
                .thenThrow(new IllegalArgumentException("Only COMPANY users can create vacancies"));

        ResponseEntity<VacancyDetailResponse> response = vacancyController.createVacancy(testRequest, jwt("1", "CANDIDATE"));

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

        VacancyDetailResponse updatedVacancy = new VacancyDetailResponse(
            1L,
            "Senior Backend Developer",
            "Java + Spring Boot + Kubernetes",
            "Bogota, Colombia",
            null,
            "REMOTE",
            "FULL_TIME",
            "SENIOR",
            List.of(),
            List.of(),
            List.of(),
            List.of(),
            6000.0,
            9000.0,
            List.of(),
            1L,
            "Tech Company Inc");

        when(vacancyService.updateVacancy(eq(1L), any(CreateVacancyRequest.class))).thenReturn(updatedVacancy);

        ResponseEntity<VacancyDetailResponse> response = vacancyController.updateVacancy(1L, updateRequest);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals("Senior Backend Developer", response.getBody().title());
        verify(vacancyService, times(1)).updateVacancy(eq(1L), any(CreateVacancyRequest.class));
    }

    @Test
    void shouldReturn400WhenUpdatingVacancyWithInvalidData() {
        when(vacancyService.updateVacancy(eq(1L), any(CreateVacancyRequest.class)))
                .thenThrow(new IllegalArgumentException("Invalid salary range"));

        ResponseEntity<VacancyDetailResponse> response = vacancyController.updateVacancy(1L, testRequest);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertNull(response.getBody());
        verify(vacancyService, times(1)).updateVacancy(eq(1L), any(CreateVacancyRequest.class));
    }

    @Test
    void shouldReturn404WhenUpdatingVacancyNotFound() {
        when(vacancyService.updateVacancy(eq(999L), any(CreateVacancyRequest.class)))
                .thenThrow(new RuntimeException("Vacancy not found"));

        ResponseEntity<VacancyDetailResponse> response = vacancyController.updateVacancy(999L, testRequest);

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

        @Test
        void shouldRegisterCompanyCandidateDecisionUsingBodyDecision() {
        CompanyCandidateDecisionRequest request = CompanyCandidateDecisionRequest.builder()
            .decision(SwipeDecisionType.DISLIKE)
            .rejectionReason("Skills gap")
            .rejectionTags(List.of("backend"))
            .missingTechnologies(List.of("Spring"))
            .missingResponsibilities(List.of("Mentoring"))
            .missingTechnicalRequirements(List.of("System design"))
            .expectedExperienceLevel("SENIOR")
            .aiSummary("Candidate lacks required scope")
            .rejectionComment("Not enough architecture depth")
            .build();

        CompanyCandidateDecisionResponse payload = CompanyCandidateDecisionResponse.builder()
            .companyId(1L)
            .candidateId(2L)
            .vacancyId(3L)
            .decision(SwipeDecisionType.DISLIKE)
            .matched(false)
            .build();

        when(vacancyService.registerCompanyCandidateDecision(
            1L,
            3L,
            2L,
            request)).thenReturn(payload);

        ResponseEntity<CompanyCandidateDecisionResponse> response = vacancyController.registerCompanyCandidateDecision(
            jwt("1", "COMPANY"),
            3L,
            2L,
            request,
            null);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(SwipeDecisionType.DISLIKE, response.getBody().getDecision());
        assertEquals(false, response.getBody().isMatched());
        }

        @Test
        void shouldRegisterCompanyCandidateDecisionUsingQueryParamWhenBodyDecisionMissing() {
        CompanyCandidateDecisionRequest request = CompanyCandidateDecisionRequest.builder()
            .rejectionReason("unused")
            .build();

        CompanyCandidateDecisionResponse payload = CompanyCandidateDecisionResponse.builder()
            .companyId(1L)
            .candidateId(2L)
            .vacancyId(3L)
            .decision(SwipeDecisionType.LIKE)
            .matched(true)
            .build();

        when(vacancyService.registerCompanyCandidateDecision(
            1L,
            3L,
            2L,
            request)).thenReturn(payload);

        ResponseEntity<CompanyCandidateDecisionResponse> response = vacancyController.registerCompanyCandidateDecision(
            jwt("1", "COMPANY"),
            3L,
            2L,
            request,
            SwipeDecisionType.LIKE);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(true, response.getBody().isMatched());
        }

        @Test
        void shouldRegisterCompanyCandidateDecisionWhenBodyIsNullAndDecisionComesFromQuery() {
        CompanyCandidateDecisionResponse payload = CompanyCandidateDecisionResponse.builder()
            .companyId(1L)
            .candidateId(2L)
            .vacancyId(3L)
            .decision(SwipeDecisionType.LIKE)
            .matched(false)
            .build();

        CompanyCandidateDecisionRequest request = CompanyCandidateDecisionRequest.builder()
            .decision(SwipeDecisionType.LIKE)
            .build();

        when(vacancyService.registerCompanyCandidateDecision(
            1L,
            3L,
            2L,
            request)).thenReturn(payload);

        ResponseEntity<CompanyCandidateDecisionResponse> response = vacancyController.registerCompanyCandidateDecision(
            jwt("1", "COMPANY"),
            3L,
            2L,
            request,
            SwipeDecisionType.LIKE);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(SwipeDecisionType.LIKE, response.getBody().getDecision());
        }

        @Test
        void shouldReturn400WhenCompanyDecisionIsMissing() {
        ResponseEntity<CompanyCandidateDecisionResponse> response = vacancyController.registerCompanyCandidateDecision(
            jwt("1", "COMPANY"),
            3L,
            2L,
            null,
            null);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        verify(vacancyService, times(0)).registerCompanyCandidateDecision(
            anyLong(), anyLong(), anyLong(), any(CompanyCandidateDecisionRequest.class));
        }

        @Test
        void shouldReturn400WhenCompanyDecisionServiceThrowsIllegalArgument() {
        CompanyCandidateDecisionRequest request = CompanyCandidateDecisionRequest.builder()
            .decision(SwipeDecisionType.LIKE)
            .build();
        when(vacancyService.registerCompanyCandidateDecision(
            1L,
            3L,
            2L,
            request)).thenThrow(new IllegalArgumentException("invalid"));

        ResponseEntity<CompanyCandidateDecisionResponse> response = vacancyController.registerCompanyCandidateDecision(
            jwt("1", "COMPANY"),
            3L,
            2L,
            request,
            null);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        }

        @Test
        void shouldReturn404WhenCompanyDecisionServiceThrowsRuntime() {
        CompanyCandidateDecisionRequest request = CompanyCandidateDecisionRequest.builder()
            .decision(SwipeDecisionType.LIKE)
            .build();
        when(vacancyService.registerCompanyCandidateDecision(
            1L,
            3L,
            2L,
            request)).thenThrow(new RuntimeException("missing"));

        ResponseEntity<CompanyCandidateDecisionResponse> response = vacancyController.registerCompanyCandidateDecision(
            jwt("1", "COMPANY"),
            3L,
            2L,
            request,
            null);

        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
        }

        @Test
        void shouldGetCandidateApplications() {
        List<CandidateApplicationResponse> applications = List.of(
            CandidateApplicationResponse.builder()
                .vacancyId(10L)
                .vacancyTitle("Backend")
                .companyId(1L)
                .companyName("Tech Co")
                .decision(SwipeDecisionType.DISLIKE)
                .matched(false)
                .build());
        when(vacancyService.getCandidateApplications(5L, 30)).thenReturn(applications);

        ResponseEntity<List<CandidateApplicationResponse>> response = vacancyController.getApplications(
            jwt("5", "CANDIDATE"),
            30);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(1, response.getBody().size());
        assertEquals(10L, response.getBody().get(0).getVacancyId());
        }

        @Test
        void shouldReturn400WhenApplicationsLimitInvalid() {
        ResponseEntity<List<CandidateApplicationResponse>> response = vacancyController.getApplications(
            jwt("5", "CANDIDATE"),
            0);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        verify(vacancyService, times(0)).getCandidateApplications(anyLong(), anyInt());
        }

    @Test
    void shouldReturn400WhenApplicationsLimitExceedsMaximum() {
        ResponseEntity<List<CandidateApplicationResponse>> response = vacancyController.getApplications(
                jwt("5", "CANDIDATE"),
                101);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        verify(vacancyService, times(0)).getCandidateApplications(anyLong(), anyInt());
    }

        @Test
        void shouldReturn400WhenApplicationsServiceThrowsIllegalArgument() {
        when(vacancyService.getCandidateApplications(5L, 30)).thenThrow(new IllegalArgumentException("invalid"));

        ResponseEntity<List<CandidateApplicationResponse>> response = vacancyController.getApplications(
            jwt("5", "CANDIDATE"),
            30);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        }

        @Test
        void shouldReturn404WhenApplicationsServiceThrowsRuntime() {
        when(vacancyService.getCandidateApplications(5L, 30)).thenThrow(new RuntimeException("missing"));

        ResponseEntity<List<CandidateApplicationResponse>> response = vacancyController.getApplications(
            jwt("5", "CANDIDATE"),
            30);

        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
        }

        @Test
        void shouldGetMatchesForUser() {
        List<UserMatchResponse> matches = List.of(
            UserMatchResponse.builder()
                .vacancyId(10L)
                .vacancyTitle("Backend")
                .counterpartId(2L)
                .counterpartName("Ana")
                .build());
        when(vacancyService.getMatchesForUser(1L, 20)).thenReturn(matches);

        ResponseEntity<List<UserMatchResponse>> response = vacancyController.getMatches(
            jwt("1", "CANDIDATE"),
            20);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(1, response.getBody().size());
        }

        @Test
        void shouldReturn400WhenMatchesLimitInvalid() {
        ResponseEntity<List<UserMatchResponse>> response = vacancyController.getMatches(
            jwt("1", "CANDIDATE"),
            101);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        verify(vacancyService, times(0)).getMatchesForUser(anyLong(), anyInt());
        }

    @Test
    void shouldReturn400WhenMatchesLimitIsNotPositive() {
        ResponseEntity<List<UserMatchResponse>> response = vacancyController.getMatches(
                jwt("1", "CANDIDATE"),
                0);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        verify(vacancyService, times(0)).getMatchesForUser(anyLong(), anyInt());
    }

        @Test
        void shouldReturn404WhenMatchesServiceThrowsRuntime() {
        when(vacancyService.getMatchesForUser(1L, 20)).thenThrow(new RuntimeException("missing"));

        ResponseEntity<List<UserMatchResponse>> response = vacancyController.getMatches(
            jwt("1", "CANDIDATE"),
            20);

        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
        }

    private Jwt jwt(String subject, String role) {
        return Jwt.withTokenValue("test-token")
                .header("alg", "HS256")
                .subject(subject)
                .claim("role", role)
                .build();
    }
}

