package ieti.JobSwipe.security;

import com.google.api.client.googleapis.auth.oauth2.GoogleIdToken;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdTokenVerifier;

import ieti.JobSwipe.config.GoogleOAuthProperties;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;
import java.security.GeneralSecurityException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GoogleIdentityTokenVerifierTest {

    @Mock
    private GoogleOAuthProperties googleOAuthProperties;

    private GoogleIdentityTokenVerifier identityTokenVerifier;
    private GoogleIdTokenVerifier mockGoogleVerifier;

    @BeforeEach
    void setUp() {
        when(googleOAuthProperties.getClientId()).thenReturn("test-client-id");
        mockGoogleVerifier = mock(GoogleIdTokenVerifier.class);
        identityTokenVerifier = new GoogleIdentityTokenVerifier(googleOAuthProperties);

        try {
            java.lang.reflect.Field verifierField = GoogleIdentityTokenVerifier.class.getDeclaredField("verifier");
            verifierField.setAccessible(true);
            verifierField.set(identityTokenVerifier, mockGoogleVerifier);
        } catch (Exception e) {
            throw new RuntimeException("Failed to set mock verifier", e);
        }
    }

    @Test
    void shouldReturnAuthenticatedUserWhenTokenIsValid() throws Exception {
        String validToken = "valid.google.token";
        GoogleIdToken mockToken = mock(GoogleIdToken.class);
        GoogleIdToken.Payload mockPayload = mock(GoogleIdToken.Payload.class);

        when(mockToken.getPayload()).thenReturn(mockPayload);
        when(mockPayload.getSubject()).thenReturn("google-subject-123");
        when(mockPayload.getEmail()).thenReturn("john@example.com");
        when(mockPayload.get("name")).thenReturn("John Doe");
        when(mockPayload.get("picture")).thenReturn("https://example.com/avatar.jpg");
        when(mockGoogleVerifier.verify(validToken)).thenReturn(mockToken);

        AuthenticatedUser result = identityTokenVerifier.verify(validToken);

        assertNotNull(result);
        assertEquals("google-subject-123", result.subject());
        assertEquals("john@example.com", result.email());
        assertEquals("John Doe", result.name());
        assertEquals("https://example.com/avatar.jpg", result.picture());
    }

    @Test
    void shouldThrowInvalidTokenExceptionWhenVerifyReturnsNull() throws Exception {
        String invalidToken = "invalid.token";

        when(mockGoogleVerifier.verify(invalidToken)).thenReturn(null);

        InvalidIdentityTokenException exception = assertThrows(InvalidIdentityTokenException.class, () -> {
            identityTokenVerifier.verify(invalidToken);
        });

        assertEquals("Invalid Google identity token", exception.getMessage());
    }

    @Test
    void shouldThrowInvalidTokenExceptionWhenEmailIsMissing() throws Exception {
        String tokenWithoutEmail = "token.without.email";
        GoogleIdToken mockToken = mock(GoogleIdToken.class);
        GoogleIdToken.Payload mockPayload = mock(GoogleIdToken.Payload.class);

        when(mockToken.getPayload()).thenReturn(mockPayload);
        when(mockPayload.getEmail()).thenReturn(null);
        when(mockGoogleVerifier.verify(tokenWithoutEmail)).thenReturn(mockToken);

        InvalidIdentityTokenException exception = assertThrows(InvalidIdentityTokenException.class, () -> {
            identityTokenVerifier.verify(tokenWithoutEmail);
        });

        assertEquals("Google identity token does not contain email", exception.getMessage());
    }

    @Test
    void shouldThrowInvalidTokenExceptionOnGeneralSecurityException() throws Exception {
        String tokenCausingSecurityError = "token.security.error";

        when(mockGoogleVerifier.verify(tokenCausingSecurityError))
                .thenThrow(new GeneralSecurityException("Security error"));

        InvalidIdentityTokenException exception = assertThrows(InvalidIdentityTokenException.class, () -> {
            identityTokenVerifier.verify(tokenCausingSecurityError);
        });

        assertEquals("Invalid Google identity token", exception.getMessage());
        assertNotNull(exception.getCause());
    }

    @Test
    void shouldThrowInvalidTokenExceptionOnIOException() throws Exception {
        String tokenCausingIOError = "token.io.error";

        when(mockGoogleVerifier.verify(tokenCausingIOError))
                .thenThrow(new IOException("IO error"));

        InvalidIdentityTokenException exception = assertThrows(InvalidIdentityTokenException.class, () -> {
            identityTokenVerifier.verify(tokenCausingIOError);
        });

        assertEquals("Invalid Google identity token", exception.getMessage());
        assertNotNull(exception.getCause());
    }
}
