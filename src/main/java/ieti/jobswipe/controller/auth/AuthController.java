package ieti.jobswipe.controller.auth;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import static org.springframework.http.HttpStatus.BAD_REQUEST;
import static org.springframework.http.HttpStatus.UNAUTHORIZED;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import ieti.jobswipe.model.Role;
import ieti.jobswipe.model.entity.User;
import ieti.jobswipe.repository.profile.ProfileRepository;
import ieti.jobswipe.security.AuthenticatedUser;
import ieti.jobswipe.security.IdentityTokenVerifier;
import ieti.jobswipe.security.InvalidIdentityTokenException;
import ieti.jobswipe.security.JwtTokenService;
import ieti.jobswipe.security.UserProvisioningService;
import ieti.jobswipe.service.user.UserService;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private static final Logger logger = LoggerFactory.getLogger(AuthController.class);

    private final IdentityTokenVerifier identityTokenVerifier;
    private final UserProvisioningService userProvisioningService;
    private final JwtTokenService jwtTokenService;
    private final UserService userService;
    private final ProfileRepository profileRepository;

    public AuthController(IdentityTokenVerifier identityTokenVerifier,
            UserProvisioningService userProvisioningService,
            JwtTokenService jwtTokenService,
            UserService userService,
            ProfileRepository profileRepository) {
        this.identityTokenVerifier = identityTokenVerifier;
        this.userProvisioningService = userProvisioningService;
        this.jwtTokenService = jwtTokenService;
        this.userService = userService;
        this.profileRepository = profileRepository;
    }

    @PostMapping("/google")
    public ResponseEntity<AuthTokenResponse> authenticateWithGoogle(@RequestBody GoogleAuthRequest request) {
        long startNanos = System.nanoTime();
        if (request == null || !StringUtils.hasText(request.idToken())) {
            throw new ResponseStatusException(BAD_REQUEST, "Google idToken is required");
        }

        long verifyStart = System.nanoTime();
        AuthenticatedUser authenticatedUser = identityTokenVerifier.verify(request.idToken());
        long verifyMs = (System.nanoTime() - verifyStart) / 1_000_000;

        long provisionStart = System.nanoTime();
        User user = userProvisioningService.ensureUserExists(authenticatedUser);
        long provisionMs = (System.nanoTime() - provisionStart) / 1_000_000;

        long jwtStart = System.nanoTime();
        JwtTokenService.TokenPayload tokenPayload = jwtTokenService.generateToken(user);
        long jwtMs = (System.nanoTime() - jwtStart) / 1_000_000;

        long profileExistsStart = System.nanoTime();
        boolean hasProfile = profileRepository.existsByUserId(user.getId());
        long profileExistsMs = (System.nanoTime() - profileExistsStart) / 1_000_000;

        long totalMs = (System.nanoTime() - startNanos) / 1_000_000;
        logger.info("⏱️ POST /api/auth/google completed in {} ms for userId={}",
            totalMs,
            user.getId());
        logger.info("⏱️ auth/google breakdown ms userId={} verify={} provision={} jwt={} profileExists={} total={}",
            user.getId(), verifyMs, provisionMs, jwtMs, profileExistsMs, totalMs);

        return ResponseEntity.ok(new AuthTokenResponse(
                tokenPayload.accessToken(),
                "Bearer",
                tokenPayload.expiresIn(),
            hasProfile,
                new UserResponse(
                        user.getId(),
                        user.getName(),
                        user.getEmail(),
                        user.getGoogleId(),
                        user.getAvatarUrl(),
                        user.getRole().name())));
    }

    @PatchMapping("/me/role")
    public ResponseEntity<AuthTokenResponse> updateMyRole(
            @RequestBody RoleUpdateRequest request,
            @AuthenticationPrincipal Jwt jwt) {
        try {
            Long userId = Long.parseLong(jwt.getSubject());
            Role role = Role.valueOf(request.role());
            User updatedUser = userService.updateUserRole(userId, role);
            JwtTokenService.TokenPayload tokenPayload = jwtTokenService.generateToken(updatedUser);
            boolean hasProfile = profileRepository.existsByUserId(updatedUser.getId());

            return ResponseEntity.ok(new AuthTokenResponse(
                    tokenPayload.accessToken(),
                    "Bearer",
                    tokenPayload.expiresIn(),
                    hasProfile,
                    new UserResponse(
                            updatedUser.getId(),
                            updatedUser.getName(),
                            updatedUser.getEmail(),
                            updatedUser.getGoogleId(),
                            updatedUser.getAvatarUrl(),
                            updatedUser.getRole().name())));
        } catch (IllegalArgumentException ex) {
            throw new ResponseStatusException(BAD_REQUEST, "Invalid role value: " + request.role());
        }
    }

    @GetMapping("/me")
    public ResponseEntity<AuthenticatedUserResponse> getAuthenticatedUser(
            @AuthenticationPrincipal Jwt jwt) {
        return ResponseEntity.ok(new AuthenticatedUserResponse(
                jwt.getClaimAsString("email"),
                jwt.getClaimAsString("name")));
    }

    @ExceptionHandler(InvalidIdentityTokenException.class)
    public ResponseEntity<AuthErrorResponse> handleInvalidIdentityToken(InvalidIdentityTokenException exception) {
        return ResponseEntity.status(UNAUTHORIZED).body(new AuthErrorResponse(exception.getMessage()));
    }

    public record GoogleAuthRequest(String idToken, String accessToken) {}
    public record RoleUpdateRequest(String role) {}
    public record AuthTokenResponse(String accessToken, String tokenType, long expiresIn, boolean hasProfile, UserResponse user) {}
    public record UserResponse(Long id, String name, String email, String googleId, String avatarUrl, String role) {}
    public record AuthenticatedUserResponse(String email, String name) {}
    public record AuthErrorResponse(String error) {}
}

