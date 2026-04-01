package ieti.JobSwipe.security;

import ieti.JobSwipe.model.Role;
import ieti.JobSwipe.model.User;
import ieti.JobSwipe.repository.UserRepository;

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
        when(userRepository.findByGoogleId("google-subject-123")).thenReturn(Optional.empty());
        when(userRepository.findByEmail("john@example.com")).thenReturn(Optional.empty());
        when(userRepository.save(any(User.class))).thenReturn(testUser);

        User result = provisioningService.ensureUserExists(testAuthenticatedUser);

        assertNotNull(result);
        assertEquals("John Doe", result.getName());
        assertEquals("john@example.com", result.getEmail());
        assertEquals("google-subject-123", result.getGoogleId());
        assertEquals(Role.CANDIDATE, result.getRole());
        assertEquals("https://example.com/avatar.jpg", result.getAvatarUrl());

        verify(userRepository, times(1)).findByGoogleId("google-subject-123");
        verify(userRepository, times(1)).findByEmail("john@example.com");
        verify(userRepository, times(2)).save(any(User.class));
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

        when(userRepository.findByGoogleId("google-subject-123")).thenReturn(Optional.of(existingUser));
        when(userRepository.save(any(User.class))).thenReturn(existingUser);

        User result = provisioningService.ensureUserExists(testAuthenticatedUser);

        assertNotNull(result);
        assertEquals(5L, result.getId());
        assertEquals("John Doe", result.getName());

        verify(userRepository, times(1)).findByGoogleId("google-subject-123");
        verify(userRepository, times(1)).save(any(User.class));
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

        when(userRepository.findByGoogleId("google-subject-123")).thenReturn(Optional.empty());
        when(userRepository.findByEmail("john@example.com")).thenReturn(Optional.of(existingUserByEmail));
        when(userRepository.save(any(User.class))).thenReturn(existingUserByEmail);

        User result = provisioningService.ensureUserExists(testAuthenticatedUser);

        assertNotNull(result);
        assertEquals(3L, result.getId());
        assertEquals("google-subject-123", result.getGoogleId());

        verify(userRepository, times(1)).findByGoogleId("google-subject-123");
        verify(userRepository, times(1)).findByEmail("john@example.com");
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

        existingUser.setName("John Doe");

        when(userRepository.findByGoogleId("google-subject-123")).thenReturn(Optional.of(existingUser));
        when(userRepository.save(any(User.class))).thenReturn(existingUser);

        User result = provisioningService.ensureUserExists(testAuthenticatedUser);

        assertNotNull(result);
        assertEquals("John Doe", result.getName());

        verify(userRepository, times(1)).findByGoogleId("google-subject-123");
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

        existingUser.setAvatarUrl("https://example.com/avatar.jpg");

        when(userRepository.findByGoogleId("google-subject-123")).thenReturn(Optional.of(existingUser));
        when(userRepository.save(any(User.class))).thenReturn(existingUser);

        User result = provisioningService.ensureUserExists(testAuthenticatedUser);

        assertNotNull(result);
        assertEquals("https://example.com/avatar.jpg", result.getAvatarUrl());

        verify(userRepository, times(1)).findByGoogleId("google-subject-123");
        verify(userRepository, times(1)).save(any(User.class));
    }

    @Test
    void shouldSetDefaultRoleForNewUser() {
        when(userRepository.findByGoogleId("google-subject-123")).thenReturn(Optional.empty());
        when(userRepository.findByEmail("john@example.com")).thenReturn(Optional.empty());
        when(userRepository.save(any(User.class))).thenReturn(testUser);

        User result = provisioningService.ensureUserExists(testAuthenticatedUser);

        assertNotNull(result);
        assertEquals(Role.CANDIDATE, result.getRole());

        verify(userRepository, times(1)).findByGoogleId("google-subject-123");
        verify(userRepository, times(1)).findByEmail("john@example.com");
        verify(userRepository, times(2)).save(any(User.class));
    }
}
