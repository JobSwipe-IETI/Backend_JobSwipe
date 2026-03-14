package ieti.JobSwipe.security;

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.util.List;

import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import com.google.api.client.googleapis.auth.oauth2.GoogleIdToken;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdTokenVerifier;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.gson.GsonFactory;

import ieti.JobSwipe.config.GoogleOAuthProperties;

@Component
public class GoogleIdentityTokenVerifier implements IdentityTokenVerifier {

    private static final List<String> GOOGLE_ISSUERS = List.of(
            "https://accounts.google.com",
            "accounts.google.com"
    );

    private final GoogleIdTokenVerifier verifier;

    public GoogleIdentityTokenVerifier(GoogleOAuthProperties googleOAuthProperties) {
        this.verifier = new GoogleIdTokenVerifier.Builder(new NetHttpTransport(), GsonFactory.getDefaultInstance())
                .setAudience(List.of(googleOAuthProperties.getClientId()))
                .setIssuers(GOOGLE_ISSUERS)
                .build();
    }

    @Override
    public AuthenticatedUser verify(String token) {
        try {
            GoogleIdToken idToken = verifier.verify(token);
            if (idToken == null) {
                throw new InvalidIdentityTokenException("Invalid Google identity token");
            }

            GoogleIdToken.Payload payload = idToken.getPayload();
            if (!StringUtils.hasText(payload.getEmail())) {
                throw new InvalidIdentityTokenException("Google identity token does not contain email");
            }

            return new AuthenticatedUser(
                    payload.getSubject(),
                    payload.getEmail(),
                    payload.get("name") != null ? payload.get("name").toString() : null
            );
        } catch (GeneralSecurityException | IOException exception) {
            throw new InvalidIdentityTokenException("Invalid Google identity token", exception);
        }
    }
}