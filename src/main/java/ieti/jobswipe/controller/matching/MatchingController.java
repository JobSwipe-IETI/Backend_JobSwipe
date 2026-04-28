package ieti.jobswipe.controller.matching;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import ieti.jobswipe.dto.matching.MatchingRequest;
import ieti.jobswipe.dto.matching.MatchingResponse;
import ieti.jobswipe.service.matching.MatchingService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@RestController
@RequestMapping("/matching")
@Tag(name = "Matching", description = "Matching between candidates and vacancies")
public class MatchingController {

    private static final Logger logger = LoggerFactory.getLogger(MatchingController.class);
    private final MatchingService matchingService;

    public MatchingController(MatchingService matchingService) {
        this.matchingService = matchingService;
    }

    @PostMapping("/calculate")
    @Operation(summary = "Calculate matching between candidate and vacancy")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Matching calculated successfully"),
        @ApiResponse(responseCode = "404", description = "Candidate profile or vacancy not found"),
        @ApiResponse(responseCode = "400", description = "Invalid request"),
        @ApiResponse(responseCode = "503", description = "AI service unavailable")
    })
    public ResponseEntity<MatchingResponse> calculateMatching(@Valid @RequestBody MatchingRequest request) {
        logger.info("POST /matching/calculate called with userId={}, vacancyId={}", 
            request.getUserId(), request.getVacancyId());
        
        try {
            MatchingResponse response = matchingService.calculateMatch(request.getUserId(), request.getVacancyId());
            logger.info("✅ Matching calculated successfully: compatibility={}%", 
                response.getCompatibilityPercentage());
            return ResponseEntity.ok(response);
        } catch (RuntimeException ex) {
            logger.error("Error calculating matching: {}", ex.getMessage());
            if (ex.getMessage().contains("not found")) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
            }
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).build();
        }
    }
}

