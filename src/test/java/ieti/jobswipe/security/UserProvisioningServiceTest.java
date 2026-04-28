package ieti.jobswipe.security;

import ieti.jobswipe.model.Role;
import ieti.jobswipe.model.entity.User;
import ieti.jobswipe.repository.user.UserRepository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.lang.reflect.Field;
import java.time.Instant;
import java.util.Map;

@ExtendWith(MockitoExtension.class)
class UserProvisioningServiceTest {

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private UserProvisioningService provisioningService;

    private AuthenticatedUser testAuthenticatedUser;
    private User testUser;

    @BeforeEach
    void setUp() {
        testAuthenticatedUser = new AuthenticatedUser(
                "google-subject-123",
                "john@example.com",
                "John Doe",
                "https://example.com/avatar.jpg");

        testUser = User.builder()
                .id(1L)
                .name("John Doe")
                .email("john@example.com")
                .googleId("google-subject-123")
                .avatarUrl("https://example.com/avatar.jpg")
                .password(null)
                .role(Role.CANDIDATE)
                .build();
    }

    @Test
    void shouldCreateNewUserWhenGoogleIdNotFound() {
        when(userRepository.findFirstByGoogleIdOrEmail("google-subject-123", "john@example.com"))
            .thenReturn(Optional.empty());
        when(userRepository.save(any(User.class))).thenReturn(testUser);

        User result = provisioningService.ensureUserExists(testAuthenticatedUser);

        assertNotNull(result);
        assertEquals("John Doe", result.getName());
        assertEquals("john@example.com", result.getEmail());
        assertEquals("google-subject-123", result.getGoogleId());
        assertEquals(Role.CANDIDATE, result.getRole());
        assertEquals("https://example.com/avatar.jpg", result.getAvatarUrl());

        verify(userRepository, times(1)).findFirstByGoogleIdOrEmail("google-subject-123", "john@example.com");
        verify(userRepository, times(1)).save(any(User.class));
    }

    @Test
    void shouldFindExistingUserByGoogleId() {
        User existingUser = User.builder()
                .id(5L)
                .name("John Doe")
                .email("john@example.com")
                .googleId("google-subject-123")
                .avatarUrl("https://example.com/avatar.jpg")
                .password(null)
                .role(Role.CANDIDATE)
                .build();

        when(userRepository.findFirstByGoogleIdOrEmail("google-subject-123", "john@example.com"))
            .thenReturn(Optional.of(existingUser));

        User result = provisioningService.ensureUserExists(testAuthenticatedUser);

        assertNotNull(result);
        assertEquals(5L, result.getId());
        assertEquals("John Doe", result.getName());

        verify(userRepository, times(1)).findFirstByGoogleIdOrEmail("google-subject-123", "john@example.com");
        verify(userRepository, times(0)).save(any(User.class));
    }

    @Test
    void shouldLinkUserByEmailWhenGoogleIdNotFound() {
        User existingUserByEmail = User.builder()
                .id(3L)
                .name("John Doe")
                .email("john@example.com")
                .googleId(null)
                .avatarUrl("https://example.com/old-avatar.jpg")
                .password(null)
                .role(Role.CANDIDATE)
                .build();

        existingUserByEmail.setGoogleId("google-subject-123");

        when(userRepository.findFirstByGoogleIdOrEmail("google-subject-123", "john@example.com"))
            .thenReturn(Optional.of(existingUserByEmail));
        when(userRepository.save(any(User.class))).thenReturn(existingUserByEmail);

        User result = provisioningService.ensureUserExists(testAuthenticatedUser);

        assertNotNull(result);
        assertEquals(3L, result.getId());
        assertEquals("google-subject-123", result.getGoogleId());

        verify(userRepository, times(1)).findFirstByGoogleIdOrEmail("google-subject-123", "john@example.com");
        verify(userRepository, times(1)).save(any(User.class));
    }

    @Test
    void shouldUpdateUserNameFromGoogleToken() {
        User existingUser = User.builder()
                .id(2L)
                .name("Old Name")
                .email("john@example.com")
                .googleId("google-subject-123")
                .avatarUrl("https://example.com/avatar.jpg")
                .password(null)
                .role(Role.CANDIDATE)
                .build();

        when(userRepository.findFirstByGoogleIdOrEmail("google-subject-123", "john@example.com"))
            .thenReturn(Optional.of(existingUser));
        when(userRepository.save(any(User.class))).thenReturn(existingUser);

        User result = provisioningService.ensureUserExists(testAuthenticatedUser);

        assertNotNull(result);
        assertEquals("John Doe", result.getName());

        verify(userRepository, times(1)).findFirstByGoogleIdOrEmail("google-subject-123", "john@example.com");
        verify(userRepository, times(1)).save(any(User.class));
    }

    @Test
    void shouldUpdateUserAvatarFromGoogleToken() {
        User existingUser = User.builder()
                .id(4L)
                .name("John Doe")
                .email("john@example.com")
                .googleId("google-subject-123")
                .avatarUrl("https://example.com/old-avatar.jpg")
                .password(null)
                .role(Role.CANDIDATE)
                .build();

        when(userRepository.findFirstByGoogleIdOrEmail("google-subject-123", "john@example.com"))
            .thenReturn(Optional.of(existingUser));
        when(userRepository.save(any(User.class))).thenReturn(existingUser);

        User result = provisioningService.ensureUserExists(testAuthenticatedUser);

        assertNotNull(result);
        assertEquals("https://example.com/avatar.jpg", result.getAvatarUrl());

        verify(userRepository, times(1)).findFirstByGoogleIdOrEmail("google-subject-123", "john@example.com");
        verify(userRepository, times(1)).save(any(User.class));
    }

    @Test
    void shouldSetDefaultRoleForNewUser() {
        when(userRepository.findFirstByGoogleIdOrEmail("google-subject-123", "john@example.com"))
            .thenReturn(Optional.empty());
        when(userRepository.save(any(User.class))).thenReturn(testUser);

        User result = provisioningService.ensureUserExists(testAuthenticatedUser);

        assertNotNull(result);
        assertEquals(Role.CANDIDATE, result.getRole());

        verify(userRepository, times(1)).findFirstByGoogleIdOrEmail("google-subject-123", "john@example.com");
        verify(userRepository, times(1)).save(any(User.class));
    }

    @Test
    void shouldKeepExistingNameAndAvatarWhenTokenHasNullOptionalFields() {
        AuthenticatedUser authenticatedUserWithoutOptionalFields = new AuthenticatedUser(
                "google-subject-123",
                "john@example.com",
                null,
                null);

        User existingUser = User.builder()
                .id(7L)
                .name("Stored Name")
                .email("john@example.com")
                .googleId("google-subject-123")
                .avatarUrl("https://example.com/stored-avatar.jpg")
                .password(null)
                .role(Role.CANDIDATE)
                .build();

        when(userRepository.findFirstByGoogleIdOrEmail("google-subject-123", "john@example.com"))
            .thenReturn(Optional.of(existingUser));

        User result = provisioningService.ensureUserExists(authenticatedUserWithoutOptionalFields);

        assertNotNull(result);
        assertEquals("Stored Name", result.getName());
        assertEquals("https://example.com/stored-avatar.jpg", result.getAvatarUrl());
        verify(userRepository, times(1)).findFirstByGoogleIdOrEmail("google-subject-123", "john@example.com");
        verify(userRepository, times(0)).save(any(User.class));
    }

    @Test
    void shouldUseEmailAsNameWhenCreatingUserAndTokenNameIsNull() {
        AuthenticatedUser userWithoutName = new AuthenticatedUser(
                "google-subject-999",
                "noname@example.com",
                null,
                "https://example.com/avatar.jpg");

        User createdUser = User.builder()
                .id(9L)
                .name("noname@example.com")
                .email("noname@example.com")
                .googleId("google-subject-999")
                .avatarUrl("https://example.com/avatar.jpg")
                .password(null)
                .role(Role.CANDIDATE)
                .build();

        when(userRepository.findFirstByGoogleIdOrEmail("google-subject-999", "noname@example.com"))
            .thenReturn(Optional.empty());
        when(userRepository.save(any(User.class))).thenReturn(createdUser);

        User result = provisioningService.ensureUserExists(userWithoutName);

        assertNotNull(result);
        assertEquals("noname@example.com", result.getName());
        verify(userRepository, times(1)).findFirstByGoogleIdOrEmail("google-subject-999", "noname@example.com");
        verify(userRepository, times(1)).save(any(User.class));
    }

        @Test
        void shouldReuseCachedUserWithoutRepositoryLookupWhenTokenDataUnchanged() {
        when(userRepository.findFirstByGoogleIdOrEmail("google-subject-123", "john@example.com"))
            .thenReturn(Optional.of(testUser));

        User first = provisioningService.ensureUserExists(testAuthenticatedUser);
        User second = provisioningService.ensureUserExists(testAuthenticatedUser);

        assertNotNull(first);
        assertNotNull(second);
        verify(userRepository, times(1)).findFirstByGoogleIdOrEmail("google-subject-123", "john@example.com");
        verify(userRepository, never()).save(any(User.class));
        }

        @Test
        void shouldUseNormalizedEmailCacheWhenGoogleIdChanges() {
        User existing = User.builder()
            .id(40L)
            .name("Cached Name")
            .email("John@Example.com")
            .googleId("google-old")
            .avatarUrl("https://old")
            .password(null)
            .role(Role.CANDIDATE)
            .build();

        AuthenticatedUser firstToken = new AuthenticatedUser(
            "google-old",
            "John@Example.com",
            "Cached Name",
            "https://old");

        AuthenticatedUser secondToken = new AuthenticatedUser(
            "google-new",
            " john@example.com ",
            "Cached Name",
            "https://old");

        when(userRepository.findFirstByGoogleIdOrEmail("google-old", "John@Example.com"))
            .thenReturn(Optional.of(existing));
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        User first = provisioningService.ensureUserExists(firstToken);
        User second = provisioningService.ensureUserExists(secondToken);

        assertNotNull(first);
        assertNotNull(second);
        assertEquals("google-new", second.getGoogleId());
        assertTrue(second.getEmail().toLowerCase().contains("john@example.com"));
        verify(userRepository, times(1)).findFirstByGoogleIdOrEmail("google-old", "John@Example.com");
        verify(userRepository, times(1)).save(any(User.class));
        }

        @Test
        void shouldEvictExpiredCachedSnapshotAndReloadUser() throws Exception {
        Field googleCacheField = UserProvisioningService.class.getDeclaredField("googleUserCache");
        googleCacheField.setAccessible(true);
        @SuppressWarnings("unchecked")
        Map<String, Object> googleCache = (Map<String, Object>) googleCacheField.get(provisioningService);

        Class<?> snapshotClass = Class.forName("ieti.jobswipe.security.UserProvisioningService$CachedUserSnapshot");
        var constructor = snapshotClass.getDeclaredConstructors()[0];
        constructor.setAccessible(true);
        Object expiredSnapshot = constructor.newInstance(
            1L,
            "Old Name",
            "john@example.com",
            "google-subject-123",
            "https://example.com/avatar.jpg",
            Role.CANDIDATE,
            Instant.now().minusSeconds(5));
        googleCache.put("google-subject-123", expiredSnapshot);

        when(userRepository.findFirstByGoogleIdOrEmail("google-subject-123", "john@example.com"))
            .thenReturn(Optional.of(testUser));

        User result = provisioningService.ensureUserExists(testAuthenticatedUser);

        assertNotNull(result);
        verify(userRepository, times(1)).findFirstByGoogleIdOrEmail("google-subject-123", "john@example.com");
        }

        @Test
        void shouldCacheByNormalizedEmailWhenGoogleIdIsBlank() {
        AuthenticatedUser userWithoutGoogleId = new AuthenticatedUser(
            " ",
            "  MIXED@Example.com  ",
            "Jane",
            null);

        User created = User.builder()
            .id(11L)
            .name("Jane")
            .email("MIXED@Example.com")
            .googleId(" ")
            .avatarUrl(null)
            .password(null)
            .role(Role.CANDIDATE)
            .build();

        when(userRepository.findFirstByGoogleIdOrEmail(" ", "  MIXED@Example.com  "))
            .thenReturn(Optional.empty());
        when(userRepository.save(any(User.class))).thenReturn(created);

        User result = provisioningService.ensureUserExists(userWithoutGoogleId);

        assertNotNull(result);
        assertEquals("MIXED@Example.com", result.getEmail());
        verify(userRepository, times(1)).save(any(User.class));
        }

    @Test
    void shouldReturnCachedUserFromEmailLookupWithoutRepositoryHit() throws Exception {
        java.lang.reflect.Field emailCacheField = UserProvisioningService.class.getDeclaredField("emailUserCache");
        emailCacheField.setAccessible(true);
        @SuppressWarnings("unchecked")
        Map<String, Object> emailCache = (Map<String, Object>) emailCacheField.get(provisioningService);

        Class<?> snapshotClass = Class.forName("ieti.jobswipe.security.UserProvisioningService$CachedUserSnapshot");
        var constructor = snapshotClass.getDeclaredConstructors()[0];
        constructor.setAccessible(true);
        Object snapshot = constructor.newInstance(
            22L,
            "Email Cached",
            "email@example.com",
            "google-email",
            null,
            Role.CANDIDATE,
            Instant.now().plusSeconds(60));
        emailCache.put("email@example.com", snapshot);

        java.lang.reflect.Method getCachedUser = UserProvisioningService.class.getDeclaredMethod("getCachedUser", String.class, String.class);
        getCachedUser.setAccessible(true);
        Object cached = getCachedUser.invoke(provisioningService, "new-google", "EMAIL@EXAMPLE.COM");

        assertNotNull(cached);
        verify(userRepository, never()).findFirstByGoogleIdOrEmail(any(), any());
    }

    @Test
    void shouldRemoveExpiredEmailCacheAndReloadFromRepository() throws Exception {
        java.lang.reflect.Field emailCacheField = UserProvisioningService.class.getDeclaredField("emailUserCache");
        emailCacheField.setAccessible(true);
        @SuppressWarnings("unchecked")
        Map<String, Object> emailCache = (Map<String, Object>) emailCacheField.get(provisioningService);

        Class<?> snapshotClass = Class.forName("ieti.jobswipe.security.UserProvisioningService$CachedUserSnapshot");
        var constructor = snapshotClass.getDeclaredConstructors()[0];
        constructor.setAccessible(true);
        Object expiredSnapshot = constructor.newInstance(
                23L,
                "Expired",
                "expired@example.com",
                "google-expired",
                null,
                Role.CANDIDATE,
                Instant.now().minusSeconds(1));
        emailCache.put("expired@example.com", expiredSnapshot);

        java.lang.reflect.Method getCachedUser = UserProvisioningService.class.getDeclaredMethod("getCachedUser", String.class, String.class);
        getCachedUser.setAccessible(true);
        Object cached = getCachedUser.invoke(provisioningService, "google-new", "expired@example.com");

        assertEquals(null, cached);
        verify(userRepository, never()).findFirstByGoogleIdOrEmail(any(), any());
    }

    @Test
    void shouldCacheOnlyWhenIdentifiersArePresentAndNormalizeNullEmail() throws Exception {
        java.lang.reflect.Method cacheUser = UserProvisioningService.class.getDeclaredMethod("cacheUser", User.class);
        cacheUser.setAccessible(true);
        User blankIdentifiers = User.builder()
                .id(44L)
                .name("Blank")
                .email(" ")
                .googleId(" ")
                .avatarUrl(null)
                .role(Role.CANDIDATE)
                .build();

        cacheUser.invoke(provisioningService, blankIdentifiers);

        java.lang.reflect.Field googleCacheField = UserProvisioningService.class.getDeclaredField("googleUserCache");
        googleCacheField.setAccessible(true);
        @SuppressWarnings("unchecked")
        Map<String, Object> googleCache = (Map<String, Object>) googleCacheField.get(provisioningService);
        assertTrue(googleCache.isEmpty());

        java.lang.reflect.Method normalizeEmail = UserProvisioningService.class.getDeclaredMethod("normalizeEmail", String.class);
        normalizeEmail.setAccessible(true);
        assertEquals("", normalizeEmail.invoke(provisioningService, new Object[] { null }));
    }

    @Test
    void shouldSkipCachingWhenIdentifiersAreNull() throws Exception {
        java.lang.reflect.Method cacheUser = UserProvisioningService.class.getDeclaredMethod("cacheUser", User.class);
        cacheUser.setAccessible(true);

        User nullIdentifiers = User.builder()
                .id(45L)
                .name("Null IDs")
                .email(null)
                .googleId(null)
                .avatarUrl(null)
                .role(Role.CANDIDATE)
                .build();

        cacheUser.invoke(provisioningService, nullIdentifiers);

        java.lang.reflect.Field googleCacheField = UserProvisioningService.class.getDeclaredField("googleUserCache");
        googleCacheField.setAccessible(true);
        @SuppressWarnings("unchecked")
        Map<String, Object> googleCache = (Map<String, Object>) googleCacheField.get(provisioningService);
        assertTrue(googleCache.isEmpty());

        java.lang.reflect.Field emailCacheField = UserProvisioningService.class.getDeclaredField("emailUserCache");
        emailCacheField.setAccessible(true);
        @SuppressWarnings("unchecked")
        Map<String, Object> emailCache = (Map<String, Object>) emailCacheField.get(provisioningService);
        assertTrue(emailCache.isEmpty());
    }
}

