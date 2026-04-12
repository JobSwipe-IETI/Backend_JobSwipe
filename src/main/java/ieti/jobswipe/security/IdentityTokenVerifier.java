package ieti.jobswipe.security;

public interface IdentityTokenVerifier {

    AuthenticatedUser verify(String token);
}
