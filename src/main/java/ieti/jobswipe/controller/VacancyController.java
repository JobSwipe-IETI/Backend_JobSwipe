package ieti.jobswipe.controller;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import ieti.jobswipe.dto.CompanyLikeActivityResponse;
import ieti.jobswipe.dto.CompanyVacancyPipelineResponse;
import ieti.jobswipe.dto.CreateVacancyRequest;
import ieti.jobswipe.dto.VacancyApplicantResponse;
import ieti.jobswipe.dto.VacancyRecommendationResponse;
import ieti.jobswipe.model.SwipeDecisionType;
import ieti.jobswipe.model.Vacancy;
import ieti.jobswipe.service.RecommendationJobService;
import ieti.jobswipe.service.VacancyService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;

@RestController
@RequestMapping("/vacancies")
@Tag(name = "Vacancies", description = "Vacancy management endpoints")
public class VacancyController {

    private static final String MESSAGE_KEY = "message";

    private final VacancyService vacancyService;
    private final RecommendationJobService recommendationJobService;

    public VacancyController(VacancyService vacancyService, RecommendationJobService recommendationJobService) {
        this.vacancyService = vacancyService;
        this.recommendationJobService = recommendationJobService;
    }

    @GetMapping
    @Operation(summary = "Get all vacancies")
    @ApiResponse(responseCode = "200", description = "Vacancies retrieved successfully")
    public ResponseEntity<List<Vacancy>> getAllVacancies(@AuthenticationPrincipal Jwt jwt) {
        Long userId = Long.parseLong(jwt.getSubject());
        return ResponseEntity.ok(vacancyService.getAllVacanciesForUser(userId));
    }

    @GetMapping("/recommended")
    @Operation(summary = "Get recommended vacancies for authenticated candidate")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Recommendations retrieved successfully"),
        @ApiResponse(responseCode = "400", description = "Invalid query params"),
        @ApiResponse(responseCode = "404", description = "Candidate profile not found")
    })
    public ResponseEntity<List<VacancyRecommendationResponse>> getRecommendedVacancies(
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam(defaultValue = "0") Float minScore,
            @RequestParam(defaultValue = "20") Integer limit) {
        if (minScore < 0 || minScore > 100 || limit <= 0) {
            return ResponseEntity.badRequest().build();
        }

        Long userId = Long.parseLong(jwt.getSubject());
        return ResponseEntity.ok(vacancyService.getRecommendedVacancies(userId, minScore, limit));
    }

    @GetMapping("/company/activity")
    @Operation(summary = "Get recent likes for company vacancies")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Company activity retrieved successfully"),
        @ApiResponse(responseCode = "400", description = "Invalid limit parameter")
    })
    public ResponseEntity<List<CompanyLikeActivityResponse>> getCompanyActivity(
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam(defaultValue = "20") Integer limit) {
        if (limit <= 0 || limit > 100) {
            return ResponseEntity.badRequest().build();
        }

        Long companyId = Long.parseLong(jwt.getSubject());
        return ResponseEntity.ok(vacancyService.getCompanyLikeActivity(companyId, limit));
    }

    @GetMapping("/company/pipeline")
    @Operation(summary = "Get company vacancies with applicant stats")
    @ApiResponse(responseCode = "200", description = "Company vacancy pipeline retrieved successfully")
    public ResponseEntity<List<CompanyVacancyPipelineResponse>> getCompanyVacancyPipeline(
            @AuthenticationPrincipal Jwt jwt) {
        Long companyId = Long.parseLong(jwt.getSubject());
        return ResponseEntity.ok(vacancyService.getCompanyVacancyPipeline(companyId));
    }

    @GetMapping("/company/vacancies/{vacancyId}/applicants")
    @Operation(summary = "Get applicants for a company vacancy with compatibility details")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Applicants retrieved successfully"),
        @ApiResponse(responseCode = "400", description = "Invalid limit parameter"),
        @ApiResponse(responseCode = "404", description = "Vacancy not found")
    })
    public ResponseEntity<List<VacancyApplicantResponse>> getApplicantsByVacancy(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable Long vacancyId,
            @RequestParam(defaultValue = "50") Integer limit) {
        if (limit <= 0 || limit > 200) {
            return ResponseEntity.badRequest().build();
        }

        Long companyId = Long.parseLong(jwt.getSubject());
        try {
            return ResponseEntity.ok(vacancyService.getApplicantsByVacancy(companyId, vacancyId, limit));
        } catch (RuntimeException ex) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }
    }

    @PostMapping("/{id}/swipe")
    @Operation(summary = "Register swipe decision (LIKE/DISLIKE) for vacancy")
    public ResponseEntity<Void> registerSwipeDecision(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable Long id,
            @RequestParam SwipeDecisionType decision) {
        Long userId = Long.parseLong(jwt.getSubject());
        vacancyService.registerSwipeDecision(userId, id, decision);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/recommended/jobs")
    @Operation(summary = "Start async recommendation job")
    public ResponseEntity<Map<String, String>> startRecommendedVacanciesJob(
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam(defaultValue = "0") Float minScore,
            @RequestParam(defaultValue = "20") Integer limit) {
        if (minScore < 0 || minScore > 100 || limit <= 0) {
            return ResponseEntity.badRequest().build();
        }

        Long userId = Long.parseLong(jwt.getSubject());
        String jobId = recommendationJobService.startJob(userId, minScore, limit);
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(Map.of("jobId", jobId));
    }

    @GetMapping("/recommended/jobs/{jobId}")
    @Operation(summary = "Get async recommendation job status")
    public ResponseEntity<Map<String, Object>> getRecommendedVacanciesJobStatus(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable String jobId) {
        Long userId = Long.parseLong(jwt.getSubject());
        Optional<RecommendationJobService.RecommendationJob> jobOpt = recommendationJobService.getJob(jobId, userId);
        if (jobOpt.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }

        RecommendationJobService.RecommendationJob job = jobOpt.get();
        return ResponseEntity.ok(Map.of(
                "jobId", job.getJobId(),
                "status", job.getStatus().name(),
                "processed", job.getProcessed(),
                "total", job.getTotal(),
                "progressPercent", job.getProgressPercent(),
                "message", job.getMessage() != null ? job.getMessage() : "",
                "error", job.getError() != null ? job.getError() : "",
                "startedAt", job.getStartedAt().toString()));
    }

    @GetMapping("/recommended/jobs/{jobId}/result")
    @Operation(summary = "Get async recommendation job result")
    public ResponseEntity<Object> getRecommendedVacanciesJobResult(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable String jobId) {
        Long userId = Long.parseLong(jwt.getSubject());
        Optional<RecommendationJobService.RecommendationJob> jobOpt = recommendationJobService.getJob(jobId, userId);
        if (jobOpt.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }

        RecommendationJobService.RecommendationJob job = jobOpt.get();
        if (job.getStatus() == RecommendationJobService.JobStatus.RUNNING) {
            return ResponseEntity.status(HttpStatus.ACCEPTED).body(Map.of(MESSAGE_KEY, "Job still running"));
        }
        if (job.getStatus() == RecommendationJobService.JobStatus.FAILED) {
            return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
                    .body(Map.of(MESSAGE_KEY, job.getError() != null ? job.getError() : "Job failed"));
        }

        return ResponseEntity.ok(job.getResult());
    }

    @GetMapping("/recommended/jobs/{jobId}/partial")
    @Operation(summary = "Get async recommendation partial result")
    public ResponseEntity<Object> getRecommendedVacanciesJobPartial(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable String jobId,
            @RequestParam(defaultValue = "0") Integer offset,
            @RequestParam(defaultValue = "10") Integer limit) {
        if (offset < 0 || limit <= 0 || limit > 100) {
            return ResponseEntity.badRequest().build();
        }

        Long userId = Long.parseLong(jwt.getSubject());
        Optional<RecommendationJobService.RecommendationJob> jobOpt = recommendationJobService.getJob(jobId, userId);
        if (jobOpt.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }

        RecommendationJobService.RecommendationJob job = jobOpt.get();
        List<VacancyRecommendationResponse> all = job.getResult();
        int safeOffset = Math.min(offset, all.size());
        int end = Math.min(safeOffset + limit, all.size());
        List<VacancyRecommendationResponse> items = all.subList(safeOffset, end);

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("jobId", job.getJobId());
        response.put("status", job.getStatus().name());
        response.put("processed", job.getProcessed());
        response.put("total", job.getTotal());
        response.put("progressPercent", job.getProgressPercent());
        response.put("message", job.getMessage() != null ? job.getMessage() : "");
        response.put("error", job.getError() != null ? job.getError() : "");
        response.put("done", job.getStatus() != RecommendationJobService.JobStatus.RUNNING);
        response.put("offset", safeOffset);
        response.put("nextOffset", end);
        response.put("items", items);

        return ResponseEntity.ok(response);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get vacancy by ID")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Vacancy found"),
        @ApiResponse(responseCode = "404", description = "Vacancy not found")
    })
    public ResponseEntity<Vacancy> getVacancyById(@PathVariable Long id) {
        try {
            return ResponseEntity.ok(vacancyService.getVacancyById(id));
        } catch (RuntimeException ex) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }
    }

    @PostMapping
    @Operation(summary = "Create a vacancy", description = "Creates a vacancy for the authenticated company user")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "201", description = "Vacancy created successfully"),
        @ApiResponse(responseCode = "400", description = "Invalid data or user is not a company"),
        @ApiResponse(responseCode = "404", description = "Company not found")
    })
    public ResponseEntity<Vacancy> createVacancy(
            @RequestBody CreateVacancyRequest request,
            @AuthenticationPrincipal Jwt jwt) {
        try {
            Long companyId = Long.parseLong(jwt.getSubject());
            Vacancy created = vacancyService.createVacancy(request, companyId);
            return ResponseEntity.status(HttpStatus.CREATED).body(created);
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
        } catch (RuntimeException ex) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }
    }

    @PutMapping("/{id}")
    @Operation(summary = "Update a vacancy")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Vacancy updated successfully"),
        @ApiResponse(responseCode = "400", description = "Invalid data"),
        @ApiResponse(responseCode = "404", description = "Vacancy not found")
    })
    public ResponseEntity<Vacancy> updateVacancy(
            @PathVariable Long id,
            @RequestBody CreateVacancyRequest request) {
        try {
            return ResponseEntity.ok(vacancyService.updateVacancy(id, request));
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
        } catch (RuntimeException ex) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Delete a vacancy")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "204", description = "Vacancy deleted successfully"),
        @ApiResponse(responseCode = "404", description = "Vacancy not found")
    })
    public ResponseEntity<Void> deleteVacancy(@PathVariable Long id) {
        try {
            vacancyService.deleteVacancy(id);
            return ResponseEntity.noContent().build();
        } catch (RuntimeException ex) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }
    }
}

