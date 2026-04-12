package ieti.jobswipe.model;

import org.junit.jupiter.api.Test;

import ieti.jobswipe.model.Profile;
import ieti.jobswipe.model.Role;
import ieti.jobswipe.model.User;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

class ProfileTest {

    private User buildUser() {
        return User.builder()
                .id(1L)
                .name("John Doe")
                .email("john@example.com")
                .password("password123")
                .role(Role.CANDIDATE)
                .build();
    }

    @Test
    void shouldCreateProfileWithBuilder() {
        User user = buildUser();

        Profile profile = Profile.builder()
                .id(1L)
                .professionalTitle("Backend Developer")
                .summary("Java and Spring Boot developer")
                .skills("Java, Spring Boot, SQL")
                .experience("3 years of backend experience")
                .education("Computer Engineering")
                .location("Bogota, Colombia")
                .user(user)
                .build();

        assertNotNull(profile);
        assertEquals(1L, profile.getId());
        assertEquals("Backend Developer", profile.getProfessionalTitle());
        assertEquals("Java and Spring Boot developer", profile.getSummary());
        assertEquals("Java, Spring Boot, SQL", profile.getSkills());
        assertEquals("3 years of backend experience", profile.getExperience());
        assertEquals("Computer Engineering", profile.getEducation());
        assertEquals("Bogota, Colombia", profile.getLocation());
        assertEquals(user, profile.getUser());
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
        assertNull(profile.getUser());
    }

    @Test
    void shouldSetAndGetProfileProperties() {
        User user = buildUser();

        Profile profile = new Profile();
        profile.setId(1L);
        profile.setProfessionalTitle("Frontend Developer");
        profile.setSummary("React and TypeScript specialist");
        profile.setSkills("React, TypeScript, Flutter");
        profile.setExperience("2 years of frontend experience");
        profile.setEducation("Software Engineering");
        profile.setLocation("Medellin, Colombia");
        profile.setUser(user);

        assertEquals(1L, profile.getId());
        assertEquals("Frontend Developer", profile.getProfessionalTitle());
        assertEquals("React and TypeScript specialist", profile.getSummary());
        assertEquals("React, TypeScript, Flutter", profile.getSkills());
        assertEquals("2 years of frontend experience", profile.getExperience());
        assertEquals("Software Engineering", profile.getEducation());
        assertEquals("Medellin, Colombia", profile.getLocation());
        assertEquals(user, profile.getUser());
    }
}
