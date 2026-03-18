package ieti.JobSwipe.security;

public interface IdentityTokenVerifier {

    AuthenticatedUser verify(String token);
}