package ieti.jobswipe.security;

public record AuthenticatedUser(String subject, String email, String name, String picture) {
}

