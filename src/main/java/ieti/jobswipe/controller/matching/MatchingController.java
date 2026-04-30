package ieti.jobswipe.controller.matching;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import ieti.jobswipe.dto.matching.MatchingRequest;
import ieti.jobswipe.dto.matching.MatchingResponse;
import ieti.jobswipe.service.matching.MatchingService;
import ieti.jobswipe.repository.user.UserRepository;
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
    private final UserRepository userRepository;

    public MatchingController(MatchingService matchingService, UserRepository userRepository) {
        this.matchingService = matchingService;
        this.userRepository = userRepository;
    }

    @PostMapping("/calculate")
    @Operation(summary = "Calculate matching between candidate and vacancy (Premium only)")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Matching calculated successfully"),
        @ApiResponse(responseCode = "403", description = "User is not premium"),
        @ApiResponse(responseCode = "404", description = "Candidate profile or vacancy not found"),
        @ApiResponse(responseCode = "400", description = "Invalid request"),
        @ApiResponse(responseCode = "503", description = "AI service unavailable")
    })
    public ResponseEntity<?> calculateMatching(@Valid @RequestBody MatchingRequest request) {
        logger.info("POST /matching/calculate called with userId={}, vacancyId={}", 
            request.getUserId(), request.getVacancyId());
        
        try {
            // Check if user is premium
            ieti.jobswipe.model.entity.User user = userRepository.findById(request.getUserId())
                    .orElseThrow(() -> new RuntimeException("User not found"));
            
            if (!user.getIsPremium()) {
                logger.warn("⚠️ Non-premium user {} attempted to calculate matching", request.getUserId());
                return ResponseEntity.status(HttpStatus.FORBIDDEN)
                        .body(java.util.Map.of("error", "Matching analysis is only available for premium users"));
            }

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

