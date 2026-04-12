package ieti.JobSwipe.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import ieti.JobSwipe.dto.CreateVacancyRequest;
import ieti.JobSwipe.dto.VacancyRecommendationResponse;
import ieti.JobSwipe.model.Vacancy;
import ieti.JobSwipe.service.VacancyService;
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

import java.util.List;

@RestController
@RequestMapping("/vacancies")
@Tag(name = "Vacancies", description = "Vacancy management endpoints")
public class VacancyController {

    private final VacancyService vacancyService;

    public VacancyController(VacancyService vacancyService) {
        this.vacancyService = vacancyService;
    }

    @GetMapping
    @Operation(summary = "Get all vacancies")
    @ApiResponse(responseCode = "200", description = "Vacancies retrieved successfully")
    public ResponseEntity<List<Vacancy>> getAllVacancies() {
        return ResponseEntity.ok(vacancyService.getAllVacancies());
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
