package IETI.JobSwipe.controller;

import IETI.JobSwipe.model.Vacancy;
import IETI.JobSwipe.service.VacancyService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
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
    @Operation(summary = "Get all vacancies", description = "Retrieve a list of all vacancies in the system")
    @ApiResponse(responseCode = "200", description = "Vacancies retrieved successfully")
    public ResponseEntity<List<Vacancy>> getAllVacancies() {
        return ResponseEntity.ok(vacancyService.getAllVacancies());
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get vacancy by ID", description = "Retrieve a specific vacancy by its ID")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Vacancy found and returned successfully"),
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
    @Operation(summary = "Create a new vacancy", description = "Create a new vacancy for a company")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "201", description = "Vacancy created successfully"),
        @ApiResponse(responseCode = "404", description = "Company not found")
    })
    public ResponseEntity<Vacancy> createVacancy(@RequestBody Vacancy vacancy,
                                                 @RequestParam Long companyId) {
        try {
            Vacancy createdVacancy = vacancyService.createVacancy(vacancy, companyId);
            return ResponseEntity.status(HttpStatus.CREATED).body(createdVacancy);
        } catch (RuntimeException ex) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }
    }

    @PutMapping("/{id}")
    @Operation(summary = "Update a vacancy", description = "Update an existing vacancy by ID")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Vacancy updated successfully"),
        @ApiResponse(responseCode = "404", description = "Vacancy not found")
    })
    public ResponseEntity<Vacancy> updateVacancy(@PathVariable Long id, @RequestBody Vacancy vacancy) {
        try {
            return ResponseEntity.ok(vacancyService.updateVacancy(id, vacancy));
        } catch (RuntimeException ex) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Delete a vacancy", description = "Delete a vacancy by ID")
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
