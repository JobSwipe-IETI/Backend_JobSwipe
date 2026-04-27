package ieti.jobswipe.security;

import com.google.api.client.googleapis.auth.oauth2.GoogleIdToken;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdTokenVerifier;

import ieti.jobswipe.config.GoogleOAuthProperties;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.time.Instant;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
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
    void shouldAllowMissingOptionalNameAndPictureClaims() throws Exception {
        String validTokenWithoutOptionalClaims = "valid.token.without.optional.claims";
        GoogleIdToken mockToken = mock(GoogleIdToken.class);
        GoogleIdToken.Payload mockPayload = mock(GoogleIdToken.Payload.class);

        when(mockToken.getPayload()).thenReturn(mockPayload);
        when(mockPayload.getSubject()).thenReturn("google-subject-456");
        when(mockPayload.getEmail()).thenReturn("jane@example.com");
        when(mockPayload.get("name")).thenReturn(null);
        when(mockPayload.get("picture")).thenReturn(null);
        when(mockGoogleVerifier.verify(validTokenWithoutOptionalClaims)).thenReturn(mockToken);

        AuthenticatedUser result = identityTokenVerifier.verify(validTokenWithoutOptionalClaims);

        assertNotNull(result);
        assertEquals("google-subject-456", result.subject());
        assertEquals("jane@example.com", result.email());
        assertNull(result.name());
        assertNull(result.picture());
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

    @Test
    void shouldReturnCachedAuthenticatedUserOnSecondVerification() throws Exception {
        String validToken = "cached.google.token";
        GoogleIdToken mockToken = mock(GoogleIdToken.class);
        GoogleIdToken.Payload mockPayload = mock(GoogleIdToken.Payload.class);

        when(mockToken.getPayload()).thenReturn(mockPayload);
        when(mockPayload.getSubject()).thenReturn("google-subject-cache");
        when(mockPayload.getEmail()).thenReturn("cache@example.com");
        when(mockPayload.get("name")).thenReturn("Cache User");
        when(mockPayload.get("picture")).thenReturn(null);
        when(mockGoogleVerifier.verify(validToken)).thenReturn(mockToken);

        AuthenticatedUser first = identityTokenVerifier.verify(validToken);
        AuthenticatedUser second = identityTokenVerifier.verify(validToken);

        assertEquals(first, second);
        verify(mockGoogleVerifier, times(1)).verify(validToken);
    }

    @Test
    void shouldIgnoreExpiredCacheEntryAndReverifyToken() throws Exception {
        String token = "expired.google.token";
        java.lang.reflect.Field cacheField = GoogleIdentityTokenVerifier.class.getDeclaredField("tokenCache");
        cacheField.setAccessible(true);
        @SuppressWarnings("unchecked")
        Map<String, Object> tokenCache = (Map<String, Object>) cacheField.get(identityTokenVerifier);

        Class<?> snapshotClass = Class.forName("ieti.jobswipe.security.GoogleIdentityTokenVerifier$CachedAuthResult");
        var constructor = snapshotClass.getDeclaredConstructors()[0];
        constructor.setAccessible(true);
        tokenCache.put(token, constructor.newInstance(
                new AuthenticatedUser("old-subject", "old@example.com", null, null),
                Instant.now().minusSeconds(1)));

        GoogleIdToken mockToken = mock(GoogleIdToken.class);
        GoogleIdToken.Payload mockPayload = mock(GoogleIdToken.Payload.class);
        when(mockToken.getPayload()).thenReturn(mockPayload);
        when(mockPayload.getSubject()).thenReturn("new-subject");
        when(mockPayload.getEmail()).thenReturn("new@example.com");
        when(mockPayload.get("name")).thenReturn("New User");
        when(mockPayload.get("picture")).thenReturn(null);
        when(mockGoogleVerifier.verify(token)).thenReturn(mockToken);

        AuthenticatedUser result = identityTokenVerifier.verify(token);

        assertEquals("new-subject", result.subject());
        verify(mockGoogleVerifier, times(1)).verify(token);
    }

    @Test
    void shouldPruneExpiredCacheEntriesWhenCacheGrowsLarge() throws Exception {
        java.lang.reflect.Field cacheField = GoogleIdentityTokenVerifier.class.getDeclaredField("tokenCache");
        cacheField.setAccessible(true);
        @SuppressWarnings("unchecked")
        Map<String, Object> tokenCache = (Map<String, Object>) cacheField.get(identityTokenVerifier);

        Class<?> snapshotClass = Class.forName("ieti.jobswipe.security.GoogleIdentityTokenVerifier$CachedAuthResult");
        var constructor = snapshotClass.getDeclaredConstructors()[0];
        constructor.setAccessible(true);
        for (int index = 0; index < 2048; index++) {
            tokenCache.put("expired-" + index, constructor.newInstance(
                    new AuthenticatedUser("subject-" + index, "user" + index + "@example.com", null, null),
                    Instant.now().minusSeconds(10)));
        }

        String validToken = "fresh-token";
        GoogleIdToken mockToken = mock(GoogleIdToken.class);
        GoogleIdToken.Payload mockPayload = mock(GoogleIdToken.Payload.class);
        when(mockToken.getPayload()).thenReturn(mockPayload);
        when(mockPayload.getSubject()).thenReturn("subject-new");
        when(mockPayload.getEmail()).thenReturn("new@example.com");
        when(mockPayload.get("name")).thenReturn("New User");
        when(mockPayload.get("picture")).thenReturn(null);
        when(mockGoogleVerifier.verify(validToken)).thenReturn(mockToken);

        AuthenticatedUser result = identityTokenVerifier.verify(validToken);

        assertEquals("subject-new", result.subject());
        verify(mockGoogleVerifier, times(1)).verify(validToken);
    }
}

