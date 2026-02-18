package IETI.JobSwipe;

import ieti.JobSwipe.JobSwipeApplication;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class JobSwipeApplicationTest {

    @Test
    void testApplicationCanBeInstantiated() {
        // Test that the application class can be instantiated
        assertDoesNotThrow(() -> {
            JobSwipeApplication app = new JobSwipeApplication();
            assertNotNull(app);
        });
    }

    @Test
    void testMainMethodExists() {
        // Test that the main method is present in the JobSwipeApplication class
        assertDoesNotThrow(() -> {
            // Verify the main method exists as a static method
            JobSwipeApplication.class.getDeclaredMethod("main", String[].class);
        });
    }
}
