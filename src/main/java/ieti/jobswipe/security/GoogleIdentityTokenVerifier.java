package ieti.jobswipe.security;

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import com.google.api.client.googleapis.auth.oauth2.GoogleIdToken;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdTokenVerifier;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.gson.GsonFactory;

import ieti.jobswipe.config.GoogleOAuthProperties;

@Component
public class GoogleIdentityTokenVerifier implements IdentityTokenVerifier {

    private static final List<String> GOOGLE_ISSUERS = List.of(
            "https://accounts.google.com",
            "accounts.google.com");

    private final GoogleIdTokenVerifier verifier;
    private final Map<String, CachedAuthResult> tokenCache = new ConcurrentHashMap<>();
    private static final long TOKEN_CACHE_TTL_SECONDS = 120;

    public GoogleIdentityTokenVerifier(GoogleOAuthProperties googleOAuthProperties) {
        this.verifier = new GoogleIdTokenVerifier.Builder(new NetHttpTransport(), GsonFactory.getDefaultInstance())
                .setAudience(List.of(googleOAuthProperties.getClientId()))
                .setIssuers(GOOGLE_ISSUERS)
                .build();
    }

    @Override
    public AuthenticatedUser verify(String token) {
        CachedAuthResult cached = tokenCache.get(token);
        if (cached != null && cached.expiresAt().isAfter(Instant.now())) {
            return cached.authenticatedUser();
        }

        try {
            GoogleIdToken idToken = verifier.verify(token);
            if (idToken == null) {
                throw new InvalidIdentityTokenException("Invalid Google identity token");
            }

            GoogleIdToken.Payload payload = idToken.getPayload();
            if (!StringUtils.hasText(payload.getEmail())) {
                throw new InvalidIdentityTokenException("Google identity token does not contain email");
            }

            AuthenticatedUser authenticatedUser = new AuthenticatedUser(
                    payload.getSubject(),
                    payload.getEmail(),
                    payload.get("name") != null ? payload.get("name").toString() : null,
                    payload.get("picture") != null ? payload.get("picture").toString() : null);

            tokenCache.put(token, new CachedAuthResult(
                    authenticatedUser,
                    Instant.now().plusSeconds(TOKEN_CACHE_TTL_SECONDS)));

            if (tokenCache.size() > 2048) {
                tokenCache.entrySet().removeIf(entry -> entry.getValue().expiresAt().isBefore(Instant.now()));
            }

            return authenticatedUser;
        } catch (GeneralSecurityException | IOException exception) {
            throw new InvalidIdentityTokenException("Invalid Google identity token", exception);
        }
    }

    private record CachedAuthResult(AuthenticatedUser authenticatedUser, Instant expiresAt) {
    }
}

