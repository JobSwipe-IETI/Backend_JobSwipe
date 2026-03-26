package ieti.JobSwipe.controller;

import ieti.JobSwipe.model.User;
import ieti.JobSwipe.security.AuthenticatedUser;
import ieti.JobSwipe.security.IdentityTokenVerifier;
import ieti.JobSwipe.security.InvalidIdentityTokenException;
import ieti.JobSwipe.security.JwtTokenService;
import ieti.JobSwipe.security.UserProvisioningService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import static org.springframework.http.HttpStatus.BAD_REQUEST;
import static org.springframework.http.HttpStatus.UNAUTHORIZED;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final IdentityTokenVerifier identityTokenVerifier;
    private final UserProvisioningService userProvisioningService;
    private final JwtTokenService jwtTokenService;

    public AuthController(IdentityTokenVerifier identityTokenVerifier,
            UserProvisioningService userProvisioningService,
            JwtTokenService jwtTokenService) {
        this.identityTokenVerifier = identityTokenVerifier;
        this.userProvisioningService = userProvisioningService;
        this.jwtTokenService = jwtTokenService;
    }

    @PostMapping("/google")
    public ResponseEntity<AuthTokenResponse> authenticateWithGoogle(@RequestBody GoogleAuthRequest request) {

        if (request == null || !StringUtils.hasText(request.idToken())) {
            throw new ResponseStatusException(BAD_REQUEST, "Google idToken is required");
        }

        AuthenticatedUser authenticatedUser = identityTokenVerifier.verify(request.idToken());
        User user = userProvisioningService.ensureUserExists(authenticatedUser);
        JwtTokenService.TokenPayload tokenPayload = jwtTokenService.generateToken(user);

        return ResponseEntity.ok(new AuthTokenResponse(
                tokenPayload.accessToken(),
                "Bearer",
                tokenPayload.expiresIn(),
                new UserResponse(
                        user.getId(),
                        user.getName(),
                        user.getEmail(),
                        user.getGoogleId(),
                        user.getAvatarUrl(),
                        user.getRole().name())));
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

    public record GoogleAuthRequest(String idToken, String accessToken) {
    }

    public record AuthTokenResponse(String accessToken, String tokenType, long expiresIn, UserResponse user) {
    }

    public record UserResponse(Long id, String name, String email, String googleId, String avatarUrl, String role) {
    }

    public record AuthenticatedUserResponse(String email, String name) {
    }

    public record AuthErrorResponse(String error) {
    }
}
