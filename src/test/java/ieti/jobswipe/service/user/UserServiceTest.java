package ieti.jobswipe.service.user;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import ieti.jobswipe.model.Role;
import ieti.jobswipe.model.entity.User;
import ieti.jobswipe.repository.user.UserRepository;
import ieti.jobswipe.scheduler.MatchingAnalysisScheduler;
   

import java.util.List;
import java.util.Arrays;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private MatchingAnalysisScheduler matchingAnalysisScheduler;

    @InjectMocks
    private UserService userService;

    private User testUser;
    @BeforeEach
    void setUp() {
        testUser = User.builder()
                .id(1L)
                .name("John Doe")
                .email("john@example.com")
                .password("password123")
                .role(Role.CANDIDATE)
                .build();
    }

    @Test
    void shouldCreateUserSuccessfully() {
        when(userRepository.save(any(User.class))).thenReturn(testUser);

        User createdUser = userService.createUser(testUser);

        assertNotNull(createdUser);
        assertEquals("John Doe", createdUser.getName());
        assertEquals("john@example.com", createdUser.getEmail());
        verify(userRepository, times(1)).save(any(User.class));
    }

    @Test
    void shouldCreateMatchingAnalysisWhenPremiumUserIsCreated() {
        User premiumUser = User.builder()
                .id(2L)
                .name("Premium User")
                .email("premium@example.com")
                .role(Role.CANDIDATE)
                .isPremium(true)
                .build();
        when(userRepository.save(any(User.class))).thenReturn(premiumUser);

        User createdUser = userService.createUser(premiumUser);

        assertNotNull(createdUser);
        verify(matchingAnalysisScheduler, times(1)).ensureMatchingAnalysisExists(premiumUser);
    }

    @Test
    void shouldReturnUserWhenUserExists() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(testUser));

        User foundUser = userService.getUserById(1L);

        assertNotNull(foundUser);
        assertEquals("John Doe", foundUser.getName());
        assertEquals(1L, foundUser.getId());
        verify(userRepository, times(1)).findById(1L);
    }

    @Test
    void shouldThrowExceptionWhenUserNotFound() {
        when(userRepository.findById(999L)).thenReturn(Optional.empty());

        RuntimeException exception = assertThrows(RuntimeException.class, () -> {
            userService.getUserById(999L);
        });

        assertEquals("User not found", exception.getMessage());
        verify(userRepository, times(1)).findById(999L);
    }

    @Test
    void shouldGetAllUsers() {
        User user2 = User.builder()
                .id(2L)
                .name("Alice")
                .email("alice@example.com")
                .password("pwd")
                .role(Role.COMPANY)
                .build();

        when(userRepository.findAll()).thenReturn(Arrays.asList(testUser, user2));

        List<User> users = userService.getAllUsers();

        assertNotNull(users);
        assertEquals(2, users.size());
        assertEquals("John Doe", users.get(0).getName());
        verify(userRepository, times(1)).findAll();
    }

    @Test
    void shouldUpdateUserSuccessfully() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(testUser));

        User update = User.builder()
                .name("Jane Doe")
                .email("jane@example.com")
                .password("newpass")
                .role(Role.COMPANY)
                .build();

        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        User updated = userService.updateUser(1L, update);

        assertNotNull(updated);
        assertEquals("Jane Doe", updated.getName());
        assertEquals("jane@example.com", updated.getEmail());
        assertEquals("newpass", updated.getPassword());
        assertEquals(Role.COMPANY, updated.getRole());
        verify(userRepository, times(1)).findById(1L);
        verify(userRepository, times(1)).save(any(User.class));
    }

    @Test
    void shouldThrowWhenUpdatingNonExistingUser() {
        when(userRepository.findById(999L)).thenReturn(Optional.empty());

        RuntimeException ex = assertThrows(RuntimeException.class, () -> userService.updateUser(999L, testUser));
        assertEquals("User not found", ex.getMessage());
        verify(userRepository, times(1)).findById(999L);
        verify(userRepository, times(0)).save(any(User.class));
    }

    @Test
    void shouldDeleteUserSuccessfully() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(testUser));

        userService.deleteUser(1L);

        verify(userRepository, times(1)).findById(1L);
        verify(userRepository, times(1)).delete(testUser);
    }

    @Test
    void shouldThrowWhenDeletingNonExistingUser() {
        when(userRepository.findById(999L)).thenReturn(Optional.empty());

        RuntimeException ex = assertThrows(RuntimeException.class, () -> userService.deleteUser(999L));
        assertEquals("User not found", ex.getMessage());
        verify(userRepository, times(1)).findById(999L);
        verify(userRepository, times(0)).delete(any(User.class));
    }

    @Test
    void shouldUpdateUserRoleSuccessfully() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(testUser));
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        User updated = userService.updateUserRole(1L, Role.COMPANY);

        assertNotNull(updated);
        assertEquals(Role.COMPANY, updated.getRole());
        verify(userRepository, times(1)).findById(1L);
        verify(userRepository, times(1)).save(any(User.class));
    }

    @Test
    void shouldThrowWhenUpdatingUserRoleForNonExistingUser() {
        when(userRepository.findById(404L)).thenReturn(Optional.empty());

        RuntimeException ex = assertThrows(RuntimeException.class, () -> userService.updateUserRole(404L, Role.COMPANY));

        assertEquals("User not found", ex.getMessage());
        verify(userRepository, times(1)).findById(404L);
        verify(userRepository, times(0)).save(any(User.class));
    }

    @Test
    void shouldUpdatePremiumStatusAndTriggerMatchingAnalysis() {
        User premiumCandidate = User.builder()
                .id(1L)
                .name("John Doe")
                .email("john@example.com")
                .password("password123")
                .role(Role.CANDIDATE)
                .isPremium(false)
                .build();

        when(userRepository.findById(1L)).thenReturn(Optional.of(premiumCandidate));
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        User updated = userService.updateUserPremiumStatus(1L, true);

        assertEquals(Boolean.TRUE, updated.getIsPremium());
        verify(userRepository, times(1)).findById(1L);
        verify(userRepository, times(1)).save(any(User.class));
        verify(matchingAnalysisScheduler, times(1)).ensureMatchingAnalysisExists(updated);
    }

    @Test
    void shouldUpdatePremiumStatusWithoutTriggerWhenNull() {
        User premiumCandidate = User.builder()
                .id(2L)
                .name("Jane Doe")
                .email("jane@example.com")
                .password("password456")
                .role(Role.CANDIDATE)
                .isPremium(true)
                .build();

        when(userRepository.findById(2L)).thenReturn(Optional.of(premiumCandidate));
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        User updated = userService.updateUserPremiumStatus(2L, null);

        assertEquals(Boolean.FALSE, updated.getIsPremium());
        verify(userRepository, times(1)).findById(2L);
        verify(userRepository, times(1)).save(any(User.class));
        verify(matchingAnalysisScheduler, never()).ensureMatchingAnalysisExists(any(User.class));
    }

    @Test
    void shouldThrowWhenUpdatingPremiumStatusForNonExistingUser() {
        when(userRepository.findById(999L)).thenReturn(Optional.empty());

        RuntimeException ex = assertThrows(RuntimeException.class,
                () -> userService.updateUserPremiumStatus(999L, true));

        assertEquals("User not found", ex.getMessage());
        verify(userRepository, times(1)).findById(999L);
        verify(userRepository, never()).save(any(User.class));
        verify(matchingAnalysisScheduler, never()).ensureMatchingAnalysisExists(any(User.class));
    }
}

