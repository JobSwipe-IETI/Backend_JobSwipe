package ieti.JobSwipe.model;

import ieti.JobSwipe.model.Vacancy;
import ieti.JobSwipe.model.User;
import ieti.JobSwipe.model.Role;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

class VacancyTest {

    private User testCompany;

    @BeforeEach
    void setUp() {
        testCompany = User.builder()
                .id(1L)
                .name("Tech Company")
                .email("company@example.com")
                .password("password123")
                .role(Role.COMPANY)
                .build();
    }

    @Test
    void shouldCreateVacancyWithBuilder() {
        Vacancy vacancy = Vacancy.builder()
                .id(1L)
                .title("Backend Developer")
                .description("Java + Spring Boot")
                .salary(5000.0)
                .company(testCompany)
                .build();

        assertNotNull(vacancy);
        assertEquals(1L, vacancy.getId());
        assertEquals("Backend Developer", vacancy.getTitle());
        assertEquals("Java + Spring Boot", vacancy.getDescription());
        assertEquals(5000.0, vacancy.getSalary());
        assertEquals(testCompany, vacancy.getCompany());
    }

    @Test
    void shouldCreateVacancyWithNoArgsConstructor() {
        Vacancy vacancy = new Vacancy();

        assertNotNull(vacancy);
        assertNull(vacancy.getId());
        assertNull(vacancy.getTitle());
        assertNull(vacancy.getDescription());
        assertNull(vacancy.getSalary());
        assertNull(vacancy.getCompany());
    }

    @Test
    void shouldSetAndGetVacancyProperties() {
        Vacancy vacancy = new Vacancy();
        vacancy.setId(1L);
        vacancy.setTitle("Frontend Developer");
        vacancy.setDescription("React + TypeScript");
        vacancy.setSalary(4500.0);
        vacancy.setCompany(testCompany);

        assertEquals(1L, vacancy.getId());
        assertEquals("Frontend Developer", vacancy.getTitle());
        assertEquals("React + TypeScript", vacancy.getDescription());
        assertEquals(4500.0, vacancy.getSalary());
        assertEquals(testCompany, vacancy.getCompany());
    }
}
