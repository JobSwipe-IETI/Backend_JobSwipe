package ieti.jobswipe.controller;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import ieti.jobswipe.dto.CandidateProfileRequest;
import ieti.jobswipe.dto.CandidateProfileResponse;
import ieti.jobswipe.dto.CompanyProfileRequest;
import ieti.jobswipe.dto.CompanyProfileResponse;
import ieti.jobswipe.dto.ProfileResponse;
import ieti.jobswipe.model.Profile;
import ieti.jobswipe.model.Role;
import ieti.jobswipe.service.ProfileService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@RestController
@RequestMapping("/profiles")
@Tag(name = "Profiles", description = "Profile management endpoints")
public class ProfileController {

    private static final Logger logger = LoggerFactory.getLogger(ProfileController.class);
    private static final Duration PROFILE_CACHE_TTL = Duration.ofSeconds(45);
    private final ProfileService profileService;
    private final Map<Long, CachedProfileResponse> profileResponseCache = new ConcurrentHashMap<>();

    public ProfileController(ProfileService profileService) {
        this.profileService = profileService;
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

