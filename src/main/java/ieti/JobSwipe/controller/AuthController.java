package ieti.JobSwipe.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import ieti.JobSwipe.security.AuthenticatedUser;

@RestController
@RequestMapping("/auth")
public class AuthController {

    @GetMapping("/me")
    public ResponseEntity<AuthenticatedUserResponse> getAuthenticatedUser(
            @AuthenticationPrincipal AuthenticatedUser authenticatedUser) {
        return ResponseEntity.ok(new AuthenticatedUserResponse(
                authenticatedUser.email(),
                authenticatedUser.name()
        ));
    }

    public record AuthenticatedUserResponse(String email, String name) {
    }
}