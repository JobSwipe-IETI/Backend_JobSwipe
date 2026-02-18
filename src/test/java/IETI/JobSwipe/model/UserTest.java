package IETI.JobSwipe.model;

import ieti.JobSwipe.model.User;
import ieti.JobSwipe.model.Role;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

class UserTest {

    @Test
    void shouldCreateUserWithBuilder() {
        User user = User.builder()
                .id(1L)
                .name("John Doe")
                .email("john@example.com")
                .password("password123")
                .role(Role.CANDIDATE)
                .build();

        assertNotNull(user);
        assertEquals(1L, user.getId());
        assertEquals("John Doe", user.getName());
        assertEquals("john@example.com", user.getEmail());
        assertEquals("password123", user.getPassword());
        assertEquals(Role.CANDIDATE, user.getRole());
    }

    @Test
    void shouldCreateUserWithNoArgsConstructor() {
        User user = new User();

        assertNotNull(user);
        assertNull(user.getId());
        assertNull(user.getName());
        assertNull(user.getEmail());
        assertNull(user.getPassword());
        assertNull(user.getRole());
    }

    @Test
    void shouldSetAndGetUserProperties() {
        User user = new User();
        user.setId(1L);
        user.setName("John Doe");
        user.setEmail("john@example.com");
        user.setPassword("password123");
        user.setRole(Role.COMPANY);

        assertEquals(1L, user.getId());
        assertEquals("John Doe", user.getName());
        assertEquals("john@example.com", user.getEmail());
        assertEquals("password123", user.getPassword());
        assertEquals(Role.COMPANY, user.getRole());
    }
}
