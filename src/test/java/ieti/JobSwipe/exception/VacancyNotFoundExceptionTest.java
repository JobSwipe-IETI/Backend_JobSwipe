package ieti.JobSwipe.exception;

import ieti.JobSwipe.exception.VacancyNotFoundException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class VacancyNotFoundExceptionTest {

    @Test
    void shouldCreateVacancyNotFoundExceptionWithMessage() {
        String message = "Vacancy not found";
        VacancyNotFoundException exception = new VacancyNotFoundException(message);

        assertNotNull(exception);
        assertEquals(message, exception.getMessage());
    }

    @Test
    void shouldCreateVacancyNotFoundExceptionWithMessageAndCause() {
        String message = "Vacancy not found";
        RuntimeException cause = new RuntimeException("Root cause");
        VacancyNotFoundException exception = new VacancyNotFoundException(message, cause);

        assertNotNull(exception);
        assertEquals(message, exception.getMessage());
        assertEquals(cause, exception.getCause());
    }

    @Test
    void shouldBeInstanceOfRuntimeException() {
        VacancyNotFoundException exception = new VacancyNotFoundException("Vacancy not found");
        
        assertNotNull(exception);
        assert exception instanceof RuntimeException;
    }
}
