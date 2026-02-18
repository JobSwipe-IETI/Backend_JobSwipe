package ieti.JobSwipe.exception;

public class ErrorMessages {
    private ErrorMessages() {
        // Private constructor to prevent instantiation
    }

    // User messages
    public static final String USER_NOT_FOUND = "User not found";
    public static final String USER_CREATION_FAILED = "User creation failed";
    public static final String USER_UPDATE_FAILED = "User update failed";
    
    // Vacancy messages
    public static final String VACANCY_NOT_FOUND = "Vacancy not found";
    public static final String VACANCY_CREATION_FAILED = "Vacancy creation failed";
    public static final String VACANCY_UPDATE_FAILED = "Vacancy update failed";
    
    // Company messages
    public static final String COMPANY_NOT_FOUND = "Company not found";
}
