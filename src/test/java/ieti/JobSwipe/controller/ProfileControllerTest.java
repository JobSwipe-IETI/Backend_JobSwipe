package ieti.JobSwipe.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import ieti.JobSwipe.dto.CandidateExperienceRequest;
import ieti.JobSwipe.dto.CandidateProfileRequest;
import ieti.JobSwipe.dto.CompanyProfileRequest;
import ieti.JobSwipe.model.Profile;
import ieti.JobSwipe.model.Role;
import ieti.JobSwipe.service.ProfileService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class ProfileControllerTest {

    @Mock
    private ProfileService profileService;

    @InjectMocks
    private ProfileController profileController;

    private MockMvc mockMvc;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(profileController).build();
        objectMapper = new ObjectMapper();
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void shouldGetProfileByUserId() throws Exception {
        Profile profile = Profile.builder()
                .id(1L)
                .professionalTitle("Backend Developer")
                .summary("Summary")
                .location("Bogota")
                .phoneNumber("3000000000")
                .onboardingCompleted(true)
                .build();

        when(profileService.getProfileByUserId(1L)).thenReturn(profile);

        mockMvc.perform(get("/profiles/user/1").contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.id", is(1)))
                .andExpect(jsonPath("$.professionalTitle", is("Backend Developer")))
                .andExpect(jsonPath("$.onboardingCompleted", is(true)));

        verify(profileService, times(1)).getProfileByUserId(1L);
    }

    @Test
    void shouldCreateCandidateProfile() throws Exception {
        CandidateProfileRequest request = new CandidateProfileRequest();
        request.setDisplayName("John Doe");
        request.setProfessionalTitle("Backend Developer");
        request.setSummary("Backend developer with extensive experience in Java, Spring Boot and APIs.");
        request.setSkills(java.util.List.of("Java", "Spring"));
        CandidateExperienceRequest experience = new CandidateExperienceRequest();
        experience.setTitle("Backend Developer");
        experience.setCompany("Acme");
        experience.setStartDate("2022-01");
        request.setExperiences(java.util.List.of(experience));
        request.setNationality("Colombia");

        Profile profile = Profile.builder()
                .id(1L)
                .professionalTitle("Backend Developer")
                .summary("Summary")
                .onboardingCompleted(true)
                .build();

        when(profileService.upsertCandidateProfile(eq(1L), any(CandidateProfileRequest.class))).thenReturn(profile);

        mockMvc.perform(post("/profiles/candidate/1")
                .with(jwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.professionalTitle", is("Backend Developer")));

        verify(profileService, times(1)).upsertCandidateProfile(eq(1L), any(CandidateProfileRequest.class));
    }

    @Test
    void shouldUpdateCompanyProfile() throws Exception {
        CompanyProfileRequest request = new CompanyProfileRequest();
        request.setCompanyName("Acme SAS");
                request.setCompanyDescription("Tech company focused on software development, cloud services and digital products.");
                request.setNationality("Colombia");

        Profile profile = Profile.builder()
                .id(1L)
                .professionalTitle("Acme SAS")
                .summary("Desc")
                .onboardingCompleted(true)
                .build();

        when(profileService.upsertCompanyProfile(eq(1L), any(CompanyProfileRequest.class))).thenReturn(profile);

        mockMvc.perform(put("/profiles/company/1")
                .with(jwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.professionalTitle", is("Acme SAS")));

        verify(profileService, times(1)).upsertCompanyProfile(eq(1L), any(CompanyProfileRequest.class));
    }

    @Test
    void shouldCreateCandidateProfileUsingJwtClaimsToResolveUser() {
        CandidateProfileRequest request = new CandidateProfileRequest();
        request.setDisplayName("Jane Doe");
        request.setProfessionalTitle("QA Engineer");
        request.setSummary("Summary long enough");
        request.setNationality("Colombia");

        Profile profile = Profile.builder().id(10L).professionalTitle("QA Engineer").build();

        when(profileService.resolveEffectiveUserId(1L, "jane@example.com", "google-123", "Jane Doe", "https://avatar", Role.CANDIDATE))
                .thenReturn(77L);
        when(profileService.upsertCandidateProfile(eq(77L), any(CandidateProfileRequest.class))).thenReturn(profile);

        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(jwtToken("1", "jane@example.com", "Jane Doe", "google-123", "https://avatar"), null));

        ResponseEntity<Profile> response = profileController.createCandidateProfile(1L, request);

        assertEquals(HttpStatus.CREATED, response.getStatusCode());
        assertEquals(10L, response.getBody().getId());
        verify(profileService, times(1))
                .resolveEffectiveUserId(1L, "jane@example.com", "google-123", "Jane Doe", "https://avatar", Role.CANDIDATE);
        verify(profileService, times(1)).upsertCandidateProfile(eq(77L), any(CandidateProfileRequest.class));
    }

    @Test
    void shouldUpdateCompanyProfileWithoutAuthenticationUsingPathUserId() {
        CompanyProfileRequest request = new CompanyProfileRequest();
        request.setCompanyName("Acme");
        request.setCompanyDescription("Description long enough");
        request.setNationality("Colombia");

        Profile profile = Profile.builder().id(22L).professionalTitle("Acme").build();
        when(profileService.upsertCompanyProfile(eq(1L), any(CompanyProfileRequest.class))).thenReturn(profile);

        ResponseEntity<Profile> response = profileController.updateCompanyProfile(1L, request);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(22L, response.getBody().getId());
        verify(profileService, never()).resolveEffectiveUserId(any(), any(), any(), any(), any(), any());
        verify(profileService, times(1)).upsertCompanyProfile(eq(1L), any(CompanyProfileRequest.class));
    }

    @Test
    void shouldReturn404WhenCreatingCandidateProfileFails() {
        CandidateProfileRequest request = new CandidateProfileRequest();
        request.setDisplayName("Jane Doe");
        request.setProfessionalTitle("QA Engineer");
        request.setSummary("Summary long enough");
        request.setNationality("Colombia");

        when(profileService.upsertCandidateProfile(eq(1L), any(CandidateProfileRequest.class)))
                .thenThrow(new RuntimeException("User not found"));

        ResponseEntity<Profile> response = profileController.createCandidateProfile(1L, request);

        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
    }

    @Test
    void shouldReturn404WhenCreatingCompanyProfileFails() {
        CompanyProfileRequest request = new CompanyProfileRequest();
        request.setCompanyName("Acme");
        request.setCompanyDescription("Description long enough");
        request.setNationality("Colombia");

        when(profileService.upsertCompanyProfile(eq(1L), any(CompanyProfileRequest.class)))
                .thenThrow(new RuntimeException("User not found"));

        ResponseEntity<Profile> response = profileController.createCompanyProfile(1L, request);

        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
    }

    private Jwt jwtToken(String subject, String email, String name, String googleId, String avatarUrl) {
        return Jwt.withTokenValue("jwt-token")
                .header("alg", "HS256")
                .subject(subject)
                .claim("email", email)
                .claim("name", name)
                .claim("googleId", googleId)
                .claim("avatarUrl", avatarUrl)
                .build();
    }

}
