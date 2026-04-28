package ieti.jobswipe.controller.auth;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import static org.springframework.http.HttpStatus.BAD_REQUEST;
import static org.springframework.http.HttpStatus.UNAUTHORIZED;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
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
    private final JwtDecoder jwtDecoder;
    private final UserService userService;
    private final ProfileRepository profileRepository;

    public AuthController(IdentityTokenVerifier identityTokenVerifier,
            UserProvisioningService userProvisioningService,
            JwtTokenService jwtTokenService,
            JwtDecoder jwtDecoder,
            UserService userService,
            ProfileRepository profileRepository) {
        this.identityTokenVerifier = identityTokenVerifier;
        this.userProvisioningService = userProvisioningService;
        this.jwtTokenService = jwtTokenService;
        this.jwtDecoder = jwtDecoder;
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
        JwtTokenService.TokenPayload accessTokenPayload = jwtTokenService.generateAccessToken(user);
        JwtTokenService.TokenPayload refreshTokenPayload = jwtTokenService.generateRefreshToken(user);
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

        return ResponseEntity.ok(buildAuthTokenResponse(
            user,
            hasProfile,
            accessTokenPayload,
            refreshTokenPayload));
    }

    @PostMapping("/refresh")
    public ResponseEntity<AuthTokenResponse> refreshSession(@RequestBody RefreshTokenRequest request) {
        if (request == null || !StringUtils.hasText(request.refreshToken())) {
            throw new ResponseStatusException(BAD_REQUEST, "Refresh token is required");
        }

        try {
            Jwt refreshJwt = jwtDecoder.decode(request.refreshToken());
            if (!"refresh".equals(refreshJwt.getClaimAsString("token_use"))) {
                throw new ResponseStatusException(UNAUTHORIZED, "Invalid refresh token");
            }

            long userId = Long.parseLong(refreshJwt.getSubject());
            User user = userService.getUserById(userId);
            boolean hasProfile = profileRepository.existsByUserId(user.getId());
            JwtTokenService.TokenPayload accessTokenPayload = jwtTokenService.generateAccessToken(user);
            JwtTokenService.TokenPayload refreshTokenPayload = jwtTokenService.generateRefreshToken(user);

            return ResponseEntity.ok(buildAuthTokenResponse(
                    user,
                    hasProfile,
                    accessTokenPayload,
                    refreshTokenPayload));
        } catch (JwtException | NumberFormatException ex) {
            throw new ResponseStatusException(UNAUTHORIZED, "Invalid refresh token");
        }
    }

    @PatchMapping("/me/role")
    public ResponseEntity<AuthTokenResponse> updateMyRole(
            @RequestBody RoleUpdateRequest request,
            @AuthenticationPrincipal Jwt jwt) {
        try {
            long userId = Long.parseLong(jwt.getSubject());
            Role role = Role.valueOf(request.role());
            User updatedUser = userService.updateUserRole(userId, role);
                JwtTokenService.TokenPayload accessTokenPayload = jwtTokenService.generateAccessToken(updatedUser);
                JwtTokenService.TokenPayload refreshTokenPayload = jwtTokenService.generateRefreshToken(updatedUser);
            boolean hasProfile = profileRepository.existsByUserId(updatedUser.getId());

                return ResponseEntity.ok(buildAuthTokenResponse(
                    updatedUser,
                    hasProfile,
                    accessTokenPayload,
                    refreshTokenPayload));
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
    public record RefreshTokenRequest(String refreshToken) {}
    public record RoleUpdateRequest(String role) {}
    public record AuthTokenResponse(
            String accessToken,
            String refreshToken,
            String tokenType,
            long expiresIn,
            long refreshExpiresIn,
            boolean hasProfile,
            UserResponse user) {}
    public record UserResponse(Long id, String name, String email, String googleId, String avatarUrl, String role) {}
    public record AuthenticatedUserResponse(String email, String name) {}
    public record AuthErrorResponse(String error) {}

    private AuthTokenResponse buildAuthTokenResponse(
            User user,
            boolean hasProfile,
            JwtTokenService.TokenPayload accessTokenPayload,
            JwtTokenService.TokenPayload refreshTokenPayload) {
        return new AuthTokenResponse(
                accessTokenPayload.accessToken(),
                refreshTokenPayload.accessToken(),
                "Bearer",
                accessTokenPayload.expiresIn(),
                refreshTokenPayload.expiresIn(),
                hasProfile,
                new UserResponse(
                        user.getId(),
                        user.getName(),
                        user.getEmail(),
                        user.getGoogleId(),
                        user.getAvatarUrl(),
                        user.getRole().name()));
    }
}

