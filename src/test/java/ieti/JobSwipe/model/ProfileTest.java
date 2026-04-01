package ieti.JobSwipe.model;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

class ProfileTest {

    @Test
    void shouldCreateProfileWithBuilder() {
        Profile profile = Profile.builder()
                .id(1L)
                .professionalTitle("Backend Developer")
                .summary("Java and Spring Boot developer")
                .skills("Java, Spring Boot, SQL")
                .experience("3 years of backend experience")
                .education("Computer Engineering")
                .location("Bogota, Colombia")
                .build();

        assertNotNull(profile);
        assertEquals(1L, profile.getId());
        assertEquals("Backend Developer", profile.getProfessionalTitle());
        assertEquals("Java and Spring Boot developer", profile.getSummary());
        assertEquals("Java, Spring Boot, SQL", profile.getSkills());
        assertEquals("3 years of backend experience", profile.getExperience());
        assertEquals("Computer Engineering", profile.getEducation());
        assertEquals("Bogota, Colombia", profile.getLocation());
    }

    @Test
    void shouldCreateProfileWithNoArgsConstructor() {
        Profile profile = new Profile();

        assertNotNull(profile);
        assertNull(profile.getId());
        assertNull(profile.getProfessionalTitle());
        assertNull(profile.getSummary());
        assertNull(profile.getSkills());
        assertNull(profile.getExperience());
        assertNull(profile.getEducation());
        assertNull(profile.getLocation());
    }

    @Test
    void shouldSetAndGetProfileProperties() {
        Profile profile = new Profile();
        profile.setId(1L);
        profile.setProfessionalTitle("Frontend Developer");
        profile.setSummary("React and TypeScript specialist");
        profile.setSkills("React, TypeScript, Flutter");
        profile.setExperience("2 years of frontend experience");
        profile.setEducation("Software Engineering");
        profile.setLocation("Medellin, Colombia");

        assertEquals(1L, profile.getId());
        assertEquals("Frontend Developer", profile.getProfessionalTitle());
        assertEquals("React and TypeScript specialist", profile.getSummary());
        assertEquals("React, TypeScript, Flutter", profile.getSkills());
        assertEquals("2 years of frontend experience", profile.getExperience());
        assertEquals("Software Engineering", profile.getEducation());
        assertEquals("Medellin, Colombia", profile.getLocation());
    }
}