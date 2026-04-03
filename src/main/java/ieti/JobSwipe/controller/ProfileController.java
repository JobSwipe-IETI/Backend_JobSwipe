package ieti.JobSwipe.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import ieti.JobSwipe.dto.CandidateProfileRequest;
import ieti.JobSwipe.dto.CompanyProfileRequest;
import ieti.JobSwipe.model.Profile;
import ieti.JobSwipe.service.ProfileService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/profiles")
@Tag(name = "Profiles", description = "Profile management endpoints")
public class ProfileController {

    private final ProfileService profileService;

    public ProfileController(ProfileService profileService) {
        this.profileService = profileService;
    }

    @GetMapping("/user/{userId}")
    @Operation(summary = "Get profile by user ID")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Profile found"),
        @ApiResponse(responseCode = "404", description = "Profile not found")
    })
    public ResponseEntity<Profile> getProfileByUserId(@PathVariable Long userId) {
        try {
            return ResponseEntity.ok(profileService.getProfileByUserId(userId));
        } catch (RuntimeException ex) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }
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
            Profile profile = profileService.upsertCandidateProfile(userId, request);
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
            Profile profile = profileService.upsertCandidateProfile(userId, request);
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
            Profile profile = profileService.upsertCompanyProfile(userId, request);
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
            Profile profile = profileService.upsertCompanyProfile(userId, request);
            return ResponseEntity.ok(profile);
        } catch (RuntimeException ex) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }
    }
}
