package ieti.jobswipe.controller;

import ieti.jobswipe.model.Role;
import ieti.jobswipe.model.User;
import ieti.jobswipe.security.AuthenticatedUser;
import ieti.jobswipe.security.IdentityTokenVerifier;
import ieti.jobswipe.security.InvalidIdentityTokenException;
import ieti.jobswipe.security.JwtTokenService;
import ieti.jobswipe.security.UserProvisioningService;
import ieti.jobswipe.service.UserService;
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

import static org.springframework.http.HttpStatus.BAD_REQUEST;
import static org.springframework.http.HttpStatus.UNAUTHORIZED;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final IdentityTokenVerifier identityTokenVerifier;
    private final UserProvisioningService userProvisioningService;
    private final JwtTokenService jwtTokenService;
    private final UserService userService;

    public AuthController(IdentityTokenVerifier identityTokenVerifier,
            UserProvisioningService userProvisioningService,
            JwtTokenService jwtTokenService,
            UserService userService) {
        this.identityTokenVerifier = identityTokenVerifier;
        this.userProvisioningService = userProvisioningService;
        this.jwtTokenService = jwtTokenService;
        this.userService = userService;
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

    @PatchMapping("/me/role")
    public ResponseEntity<AuthTokenResponse> updateMyRole(
            @RequestBody RoleUpdateRequest request,
            @AuthenticationPrincipal Jwt jwt) {
        try {
            Long userId = Long.parseLong(jwt.getSubject());
            Role role = Role.valueOf(request.role());
            User updatedUser = userService.updateUserRole(userId, role);
            JwtTokenService.TokenPayload tokenPayload = jwtTokenService.generateToken(updatedUser);

            return ResponseEntity.ok(new AuthTokenResponse(
                    tokenPayload.accessToken(),
                    "Bearer",
                    tokenPayload.expiresIn(),
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
    public record AuthTokenResponse(String accessToken, String tokenType, long expiresIn, UserResponse user) {}
    public record UserResponse(Long id, String name, String email, String googleId, String avatarUrl, String role) {}
    public record AuthenticatedUserResponse(String email, String name) {}
    public record AuthErrorResponse(String error) {}
}

