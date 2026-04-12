package ieti.jobswipe.exception;

public class VacancyNotFoundException extends RuntimeException {
    public VacancyNotFoundException(String message) {
        super(message);
    }

    public VacancyNotFoundException(String message, Throwable cause) {
        super(message, cause);
    }
}

