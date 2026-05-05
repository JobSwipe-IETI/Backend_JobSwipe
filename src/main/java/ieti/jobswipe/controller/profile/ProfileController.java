package ieti.jobswipe.controller.profile;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import ieti.jobswipe.dto.company.CompanyProfileRequest;
import ieti.jobswipe.dto.company.CompanyProfileResponse;
import ieti.jobswipe.dto.profile.CandidateProfileRequest;
import ieti.jobswipe.dto.profile.CandidateProfileResponse;
import ieti.jobswipe.dto.profile.ProfileResponse;
import ieti.jobswipe.model.Role;
import ieti.jobswipe.model.entity.Profile;
import ieti.jobswipe.repository.profile.ProfileFeedbackRepository;
import ieti.jobswipe.repository.user.UserRepository;
import ieti.jobswipe.service.profile.ProfileFeedbackService;
import ieti.jobswipe.service.profile.ProfileService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/profiles")
@Tag(name = "Profiles", description = "Profile management endpoints")
public class ProfileController {

    private static final Logger logger = LoggerFactory.getLogger(ProfileController.class);
    private static final Duration PROFILE_CACHE_TTL = Duration.ofSeconds(45);
    private final ProfileService profileService;
    private final ProfileFeedbackRepository profileFeedbackRepository;
    private final ProfileFeedbackService profileFeedbackService;
    private final UserRepository userRepository;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final Map<Long, CachedProfileResponse> profileResponseCache = new ConcurrentHashMap<>();

    public ProfileController(ProfileService profileService,
            ProfileFeedbackRepository profileFeedbackRepository,
            ProfileFeedbackService profileFeedbackService,
            UserRepository userRepository) {
        this.profileService = profileService;
        this.profileFeedbackRepository = profileFeedbackRepository;
        this.profileFeedbackService = profileFeedbackService;
        this.userRepository = userRepository;
    }

    @GetMapping("/user/{userId}")
    @Operation(summary = "Get profile by user ID")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Profile found"),
        @ApiResponse(responseCode = "404", description = "Profile not found")
    })
    public ResponseEntity<ProfileResponse> getProfileByUserId(@PathVariable Long userId) {
        long startNanos = System.nanoTime();
        logger.info("🔍 GET /profiles/user/{} called with userId={}", userId, userId);

        CachedProfileResponse cached = profileResponseCache.get(userId);
        if (cached != null && cached.expiresAt().isAfter(Instant.now())) {
            long totalMs = (System.nanoTime() - startNanos) / 1_000_000;
            logger.info("⚡ GET /profiles/user/{} cache-hit in {} ms", userId, totalMs);
            return ResponseEntity.ok(cached.response());
        }

        try {
            long serviceStart = System.nanoTime();
            Profile profile = profileService.getProfileByUserId(userId);
            long serviceMs = (System.nanoTime() - serviceStart) / 1_000_000;

            long mapStart = System.nanoTime();
            ProfileResponse response = mapProfileResponse(profile);
            long mapMs = (System.nanoTime() - mapStart) / 1_000_000;
            profileResponseCache.put(userId, new CachedProfileResponse(
                    response,
                    Instant.now().plus(PROFILE_CACHE_TTL)));

            logger.info("✅ Profile found: id={}", profile.getId());
            long totalMs = (System.nanoTime() - startNanos) / 1_000_000;
            logger.info("⏱️ GET /profiles/user/{} completed in {} ms", userId, totalMs);
            logger.info("⏱️ profiles/user breakdown ms userId={} service={} map={} total={}",
                userId, serviceMs, mapMs, totalMs);
            return ResponseEntity.ok(response);
        } catch (RuntimeException ex) {
            logger.error("❌ Profile not found for userId={}: {}", userId, ex.getMessage());
            logger.info("⏱️ GET /profiles/user/{} completed in {} ms", userId,
                    (System.nanoTime() - startNanos) / 1_000_000);
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }
    }

    @GetMapping("/user/{userId}/status")
    @Operation(summary = "Get lightweight profile status by user ID")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Status resolved")
    })
    public ResponseEntity<ProfileStatusResponse> getProfileStatusByUserId(@PathVariable Long userId) {
        long startNanos = System.nanoTime();
        boolean hasProfile = profileService.hasProfileByUserId(userId);
        logger.info("⏱️ GET /profiles/user/{}/status completed in {} ms (hasProfile={})", userId,
                (System.nanoTime() - startNanos) / 1_000_000, hasProfile);
        return ResponseEntity.ok(new ProfileStatusResponse(hasProfile));
    }

    @PostMapping("/candidate/{userId}")
    @Operation(summary = "Create or update candidate profile")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "201", description = "Candidate profile stored"),
        @ApiResponse(responseCode = "404", description = "User not found"),
        @ApiResponse(responseCode = "400", description = "Invalid request")
    })
    @CacheEvict(value = "vacancySummariesForUser", key = "#userId", allEntries = false)
    public ResponseEntity<Profile> createCandidateProfile(
            @PathVariable Long userId,
            @Valid @RequestBody CandidateProfileRequest request) {
        try {
            final Long effectiveUserId = resolveEffectiveUserId(userId, Role.CANDIDATE);
            Profile profile = profileService.upsertCandidateProfile(effectiveUserId, request);
            invalidateProfileCache(userId, effectiveUserId);
            return ResponseEntity.status(HttpStatus.CREATED).body(profile);
        } catch (RuntimeException ex) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }
    }

    @PutMapping("/candidate/{userId}")
    @Operation(summary = "Update candidate profile")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Candidate profile updated"),
        @ApiResponse(responseCode = "404", description = "User/profile not found"),
        @ApiResponse(responseCode = "400", description = "Invalid request")
    })
    @CacheEvict(value = "vacancySummariesForUser", key = "#userId", allEntries = false)
    public ResponseEntity<Profile> updateCandidateProfile(
            @PathVariable Long userId,
            @Valid @RequestBody CandidateProfileRequest request) {
        try {
            final Long effectiveUserId = resolveEffectiveUserId(userId, Role.CANDIDATE);
            Profile profile = profileService.upsertCandidateProfile(effectiveUserId, request);
            invalidateProfileCache(userId, effectiveUserId);
            return ResponseEntity.ok(profile);
        } catch (RuntimeException ex) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }
    }


    @PostMapping("/company/{userId}")
    @Operation(summary = "Create or update company profile")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "201", description = "Company profile stored"),
        @ApiResponse(responseCode = "404", description = "User not found"),
        @ApiResponse(responseCode = "400", description = "Invalid request")
    })
    @CacheEvict(value = "vacancySummariesForUser", key = "#userId", allEntries = false)
    public ResponseEntity<Profile> createCompanyProfile(
            @PathVariable Long userId,
            @Valid @RequestBody CompanyProfileRequest request) {
        try {
            final Long effectiveUserId = resolveEffectiveUserId(userId, Role.COMPANY);
            Profile profile = profileService.upsertCompanyProfile(effectiveUserId, request);
            invalidateProfileCache(userId, effectiveUserId);
            return ResponseEntity.status(HttpStatus.CREATED).body(profile);
        } catch (RuntimeException ex) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }
    }

    @PutMapping("/company/{userId}")
    @Operation(summary = "Update company profile")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Company profile updated"),
        @ApiResponse(responseCode = "404", description = "User/profile not found"),
        @ApiResponse(responseCode = "400", description = "Invalid request")
    })
    @CacheEvict(value = "vacancySummariesForUser", key = "#userId", allEntries = false)
    public ResponseEntity<Profile> updateCompanyProfile(
            @PathVariable Long userId,
            @Valid @RequestBody CompanyProfileRequest request) {
        try {
            final Long effectiveUserId = resolveEffectiveUserId(userId, Role.COMPANY);
            Profile profile = profileService.upsertCompanyProfile(effectiveUserId, request);
            invalidateProfileCache(userId, effectiveUserId);
            return ResponseEntity.ok(profile);
        } catch (RuntimeException ex) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }
    }

    private void invalidateProfileCache(Long... userIds) {
        if (userIds == null) {
            return;
        }
        for (Long id : userIds) {
            if (id != null) {
                profileResponseCache.remove(id);
            }
        }
    }

    @GetMapping("/user/{userId}/feedback")
    @Operation(summary = "Get AI feedback for a user's profile (Premium only)")
    @SuppressWarnings("unchecked")
    @Transactional(readOnly = true)
    public ResponseEntity<Map<String, Object>> getProfileFeedback(@PathVariable Long userId) {
        try {
            // Check if user is premium
            ieti.jobswipe.model.entity.User user = userRepository.findById(userId)
                    .orElseThrow(() -> new RuntimeException("User not found"));
            
            if (!Boolean.TRUE.equals(user.getIsPremium())) {
                logger.warn("⚠️ Non-premium user {} attempted to access feedback", userId);
                return ResponseEntity.status(HttpStatus.FORBIDDEN)
                        .body(Map.of("error", "This feature is only available for premium users"));
            }

            Profile profile = profileService.getProfileByUserId(userId);
            logger.info("🔎 Loading AI feedback for userId={} profileId={}", userId, profile.getId());
            Optional<String> payload = profileFeedbackRepository.findPayloadByProfileId(profile.getId());
            if (payload.isEmpty()) {
                logger.info("ℹ️ No feedback stored yet for userId={} profileId={}", userId, profile.getId());
                return ResponseEntity.noContent().build();
            }
            Map<String, Object> json = objectMapper.readValue(payload.get(), Map.class);
            logger.info("✅ Returning AI feedback for userId={} profileId={}", userId, profile.getId());
            return ResponseEntity.ok(json);
        } catch (RuntimeException ex) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        } catch (JsonProcessingException ex) {
            logger.error("Failed to parse feedback JSON: {}", ex.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @PostMapping("/user/{userId}/feedback/analyze")
    @Operation(summary = "Trigger re-analysis of a user's profile (Premium only, async)")
    public ResponseEntity<Map<String, String>> reAnalyzeProfile(@PathVariable Long userId) {
        try {
            // Check if user is premium
            ieti.jobswipe.model.entity.User user = userRepository.findById(userId)
                    .orElseThrow(() -> new RuntimeException("User not found"));
            
            if (!Boolean.TRUE.equals(user.getIsPremium())) {
                logger.warn("⚠️ Non-premium user {} attempted to trigger analysis", userId);
                return ResponseEntity.status(HttpStatus.FORBIDDEN)
                        .body(Map.of("error", "This feature is only available for premium users"));
            }

            Profile profile = profileService.getProfileByUserId(userId);
            logger.info("🔄 Premium-triggered profile analysis requested for userId={} profileId={}",
                    userId, profile.getId());
            profileFeedbackService.analyzeAndSaveAsync(profile.getId());
            return ResponseEntity.accepted().body(Map.of("message", "Analysis triggered"));
        } catch (RuntimeException ex) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }
    }

    private Long resolveEffectiveUserId(Long userId, Role fallbackRole) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof Jwt jwt) {
            return profileService.resolveEffectiveUserId(
                    userId,
                    jwt.getClaimAsString("email"),
                    jwt.getClaimAsString("googleId"),
                    jwt.getClaimAsString("name"),
                    jwt.getClaimAsString("avatarUrl"),
                    fallbackRole);
        }

        return userId;
    }

    private ProfileResponse mapProfileResponse(Profile profile) {
    CandidateProfileResponse candidateProfile = profile.getCandidateProfile() != null
        ? new CandidateProfileResponse(
            profile.getCandidateProfile().getId(),
            profile.getCandidateProfile().getLanguages(),
            profile.getCandidateProfile().getExpectedSalary(),
            profile.getCandidateProfile().getAvailability(),
            profile.getCandidateProfile().getSector(),
            profile.getCandidateProfile().getPortfolioUrl(),
            profile.getCandidateProfile().getGithubUrl(),
            profile.getCandidateProfile().getLinkedinUrl(),
            profile.getCandidateProfile().getCvUrl(),
            profile.getCandidateProfile().getCreatedAt(),
            profile.getCandidateProfile().getUpdatedAt())
        : null;

    CompanyProfileResponse companyProfile = profile.getCompanyProfile() != null
        ? new CompanyProfileResponse(
            profile.getCompanyProfile().getId(),
            profile.getCompanyProfile().getCompanyName(),
            profile.getCompanyProfile().getLegalId(),
            profile.getCompanyProfile().getIndustry(),
            profile.getCompanyProfile().getCompanySize(),
            profile.getCompanyProfile().getWebsite(),
            profile.getCompanyProfile().getHeadquartersLocation(),
            profile.getCompanyProfile().getCompanyDescription(),
            profile.getCompanyProfile().getHiringContactName(),
            profile.getCompanyProfile().getHiringContactEmail(),
            profile.getCompanyProfile().getCreatedAt(),
            profile.getCompanyProfile().getUpdatedAt())
        : null;

    return new ProfileResponse(
        profile.getId(),
        profile.getProfessionalTitle(),
        profile.getSummary(),
        profile.getSkills(),
        profile.getExperience(),
        profile.getEducation(),
        profile.getLocation(),
        profile.getNationality(),
        profile.getPhoneNumber(),
        profile.getOnboardingCompleted(),
        profile.getCreatedAt(),
        profile.getUpdatedAt(),
        candidateProfile,
        companyProfile);
    }

    public record ProfileStatusResponse(boolean hasProfile) {
    }

    private record CachedProfileResponse(ProfileResponse response, Instant expiresAt) {
    }
}

