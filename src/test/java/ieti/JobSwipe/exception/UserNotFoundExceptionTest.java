package ieti.JobSwipe.exception;

import ieti.JobSwipe.exception.UserNotFoundException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class UserNotFoundExceptionTest {

    @Test
    void shouldCreateUserNotFoundExceptionWithMessage() {
        String message = "User not found";
        UserNotFoundException exception = new UserNotFoundException(message);

        assertNotNull(exception);
        assertEquals(message, exception.getMessage());
    }

    @Test
    void shouldCreateUserNotFoundExceptionWithMessageAndCause() {
        String message = "User not found";
        RuntimeException cause = new RuntimeException("Root cause");
        UserNotFoundException exception = new UserNotFoundException(message, cause);

        assertNotNull(exception);
        assertEquals(message, exception.getMessage());
        assertEquals(cause, exception.getCause());
    }

    @Test
    void shouldBeInstanceOfRuntimeException() {
        UserNotFoundException exception = new UserNotFoundException("User not found");
        
        assertNotNull(exception);
        assert exception instanceof RuntimeException;
    }
}
