package ieti.jobswipe.security;

import ieti.jobswipe.model.Role;
import ieti.jobswipe.model.User;
import ieti.jobswipe.repository.UserRepository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

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
}

