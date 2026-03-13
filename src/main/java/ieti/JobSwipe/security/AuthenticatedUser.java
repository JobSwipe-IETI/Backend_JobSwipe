package ieti.JobSwipe.security;

public record AuthenticatedUser(String subject, String email, String name) {
}