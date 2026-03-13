package IETI.JobSwipe.auth;

public record UserProfileResponse(
    String subject,
    String email,
    String name
) {
}
