package ieti.jobswipe.model;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;


import java.util.List;

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
                .location("Bogotá, Colombia")
                .modality(Modality.REMOTE)
                .employmentType(EmploymentType.FULL_TIME)
                .experienceLevel(ExperienceLevel.SENIOR)
                .minSalary(5000.0)
                .maxSalary(7000.0)
                .company(testCompany)
                .build();

        assertNotNull(vacancy);
        assertEquals(1L, vacancy.getId());
        assertEquals("Backend Developer", vacancy.getTitle());
        assertEquals("Java + Spring Boot", vacancy.getDescription());
        assertEquals("Bogotá, Colombia", vacancy.getLocation());
        assertEquals(Modality.REMOTE, vacancy.getModality());
        assertEquals(EmploymentType.FULL_TIME, vacancy.getEmploymentType());
        assertEquals(ExperienceLevel.SENIOR, vacancy.getExperienceLevel());
        assertEquals(5000.0, vacancy.getMinSalary());
        assertEquals(7000.0, vacancy.getMaxSalary());
        assertEquals(testCompany, vacancy.getCompany());
    }

    @Test
    void shouldCreateVacancyWithNoArgsConstructor() {
        Vacancy vacancy = new Vacancy();

        assertNotNull(vacancy);
        assertNull(vacancy.getId());
        assertNull(vacancy.getTitle());
        assertNull(vacancy.getDescription());
        assertNull(vacancy.getLocation());
        assertNull(vacancy.getMinSalary());
        assertNull(vacancy.getMaxSalary());
        assertNull(vacancy.getCompany());
    }

    @Test
    void shouldSetAndGetVacancyProperties() {
        Vacancy vacancy = new Vacancy();
        vacancy.setId(1L);
        vacancy.setTitle("Frontend Developer");
        vacancy.setDescription("React + TypeScript");
        vacancy.setLocation("Medellín, Colombia");
        vacancy.setModality(Modality.HYBRID);
        vacancy.setEmploymentType(EmploymentType.PART_TIME);
        vacancy.setExperienceLevel(ExperienceLevel.JUNIOR);
        vacancy.setMinSalary(3000.0);
        vacancy.setMaxSalary(4500.0);
        vacancy.setTechnologies(List.of("React", "TypeScript"));
        vacancy.setSoftSkills(List.of("Comunicación", "Trabajo en equipo"));
        vacancy.setCompany(testCompany);

        assertEquals(1L, vacancy.getId());
        assertEquals("Frontend Developer", vacancy.getTitle());
        assertEquals("React + TypeScript", vacancy.getDescription());
        assertEquals("Medellín, Colombia", vacancy.getLocation());
        assertEquals(Modality.HYBRID, vacancy.getModality());
        assertEquals(EmploymentType.PART_TIME, vacancy.getEmploymentType());
        assertEquals(ExperienceLevel.JUNIOR, vacancy.getExperienceLevel());
        assertEquals(3000.0, vacancy.getMinSalary());
        assertEquals(4500.0, vacancy.getMaxSalary());
        assertEquals(2, vacancy.getTechnologies().size());
        assertEquals(testCompany, vacancy.getCompany());
    }
}

