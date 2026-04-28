package ieti.jobswipe.controller.profile;

import com.fasterxml.jackson.databind.ObjectMapper;


import ieti.jobswipe.dto.company.CompanyProfileRequest;
import ieti.jobswipe.dto.profile.CandidateExperienceRequest;
import ieti.jobswipe.dto.profile.CandidateProfileRequest;
import ieti.jobswipe.dto.profile.ProfileResponse;
import ieti.jobswipe.model.entity.CandidateProfile;
import ieti.jobswipe.model.entity.CompanyProfile;
import ieti.jobswipe.model.entity.Profile;
import ieti.jobswipe.model.Role;
import ieti.jobswipe.service.profile.ProfileService;
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
import org.springframework.test.util.ReflectionTestUtils;

import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
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
    void shouldReturn404WhenProfileByUserIdIsMissing() {
        when(profileService.getProfileByUserId(99L)).thenThrow(new RuntimeException("Profile not found"));

        ResponseEntity<ProfileResponse> response = profileController.getProfileByUserId(99L);

        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
        verify(profileService, times(1)).getProfileByUserId(99L);
    }

    @Test
    void shouldMapCandidateAndCompanyNestedProfilesInResponse() {
        CandidateProfile candidateProfile = CandidateProfile.builder()
                .id(101L)
                .languages("en,es")
                .expectedSalary(5000.0)
                .availability("Immediate")
                .sector("Tech")
                .portfolioUrl("https://portfolio")
                .githubUrl("https://github.com/user")
                .linkedinUrl("https://linkedin.com/in/user")
                .cvUrl("https://cdn/cv.pdf")
                .build();

        CompanyProfile companyProfile = CompanyProfile.builder()
                .id(202L)
                .companyName("Acme")
                .legalId("NIT-123")
                .industry("Software")
                .companySize("50-100")
                .website("https://acme.com")
                .headquartersLocation("Bogota")
                .companyDescription("Product company")
                .hiringContactName("Ana")
                .hiringContactEmail("ana@acme.com")
                .build();

        Profile profile = Profile.builder()
                .id(1L)
                .professionalTitle("Engineer")
                .summary("Summary")
                .skills("Java")
                .experience("3 years")
                .education("CS")
                .location("Bogota")
                .nationality("Colombia")
                .phoneNumber("300000")
                .onboardingCompleted(true)
                .candidateProfile(candidateProfile)
                .companyProfile(companyProfile)
                .build();

        when(profileService.getProfileByUserId(55L)).thenReturn(profile);

        ResponseEntity<ProfileResponse> response = profileController.getProfileByUserId(55L);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(101L, response.getBody().candidateProfile().id());
        assertEquals("Acme", response.getBody().companyProfile().companyName());
    }

    @Test
    void shouldUseCacheOnRepeatedGetProfileByUserId() {
        Profile profile = Profile.builder()
                .id(1L)
                .professionalTitle("Backend Developer")
                .summary("Summary")
                .onboardingCompleted(true)
                .build();
        when(profileService.getProfileByUserId(70L)).thenReturn(profile);

        ResponseEntity<ProfileResponse> first = profileController.getProfileByUserId(70L);
        ResponseEntity<ProfileResponse> second = profileController.getProfileByUserId(70L);

        assertEquals(HttpStatus.OK, first.getStatusCode());
        assertEquals(HttpStatus.OK, second.getStatusCode());
        verify(profileService, times(1)).getProfileByUserId(70L);
    }

        @Test
        void shouldReloadWhenCachedProfileIsExpired() {
                Profile profile = Profile.builder()
                                .id(1L)
                                .professionalTitle("Expired Cache")
                                .summary("Summary")
                                .onboardingCompleted(true)
                                .build();
                when(profileService.getProfileByUserId(81L)).thenReturn(profile);

                ResponseEntity<ProfileResponse> first = profileController.getProfileByUserId(81L);
                @SuppressWarnings("unchecked")
                java.util.Map<Long, Object> cache = (java.util.Map<Long, Object>) ReflectionTestUtils.getField(profileController, "profileResponseCache");
                Object cached = cache.get(81L);
                try {
                        java.lang.reflect.Constructor<?> constructor = cached.getClass().getDeclaredConstructors()[0];
                        constructor.setAccessible(true);
                        Object expired = constructor.newInstance(first.getBody(), java.time.Instant.now().minusSeconds(5));
                        cache.put(81L, expired);
                } catch (Exception exception) {
                        throw new RuntimeException(exception);
                }

                ResponseEntity<ProfileResponse> second = profileController.getProfileByUserId(81L);

                assertEquals(HttpStatus.OK, first.getStatusCode());
                assertEquals(HttpStatus.OK, second.getStatusCode());
                verify(profileService, times(2)).getProfileByUserId(81L);
        }

        @Test
        void shouldIgnoreNullsWhenInvalidatingProfileCache() {
                @SuppressWarnings("unchecked")
                java.util.Map<Long, Object> cache = (java.util.Map<Long, Object>) ReflectionTestUtils.getField(profileController, "profileResponseCache");
                cache.put(99L, new Object());

                assertDoesNotThrow(() -> {
                        ReflectionTestUtils.invokeMethod(profileController, "invalidateProfileCache", new Object[] { null });
                        ReflectionTestUtils.invokeMethod(profileController, "invalidateProfileCache", new Object[] { new Long[] { null, 1L } });
                });
                assertEquals(1, cache.size());
        }

        @Test
        void shouldIgnoreNullVarargsWhenInvalidatingProfileCache() throws Exception {
                @SuppressWarnings("unchecked")
                java.util.Map<Long, Object> cache = (java.util.Map<Long, Object>) ReflectionTestUtils.getField(profileController, "profileResponseCache");
                cache.put(77L, new Object());
                java.lang.reflect.Method method = ProfileController.class.getDeclaredMethod("invalidateProfileCache", Long[].class);
                method.setAccessible(true);

                assertDoesNotThrow(() -> method.invoke(profileController, new Object[] { null }));
                assertEquals(1, cache.size());
        }

        @Test
        void shouldGetProfileStatusByUserId() throws Exception {
                when(profileService.hasProfileByUserId(1L)).thenReturn(true);

                mockMvc.perform(get("/profiles/user/1/status").contentType(MediaType.APPLICATION_JSON))
                                .andExpect(status().isOk())
                                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                                .andExpect(jsonPath("$.hasProfile", is(true)));

                verify(profileService, times(1)).hasProfileByUserId(1L);
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
    void shouldUpdateCandidateProfile() {
        CandidateProfileRequest request = new CandidateProfileRequest();
        request.setDisplayName("John Doe");
        request.setProfessionalTitle("Backend Developer");
        request.setSummary("Experienced backend developer");
        request.setNationality("Colombia");

        Profile profile = Profile.builder()
                .id(15L)
                .professionalTitle("Backend Developer")
                .summary("Experienced backend developer")
                .build();

        when(profileService.upsertCandidateProfile(eq(1L), any(CandidateProfileRequest.class))).thenReturn(profile);

        ResponseEntity<Profile> response = profileController.updateCandidateProfile(1L, request);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(15L, response.getBody().getId());
        verify(profileService, times(1)).upsertCandidateProfile(eq(1L), any(CandidateProfileRequest.class));
    }

    @Test
    void shouldReturn404WhenUpdatingCandidateProfileFails() {
        CandidateProfileRequest request = new CandidateProfileRequest();
        request.setDisplayName("John Doe");
        request.setProfessionalTitle("Backend Developer");
        request.setSummary("Experienced backend developer");
        request.setNationality("Colombia");

        when(profileService.upsertCandidateProfile(eq(1L), any(CandidateProfileRequest.class)))
                .thenThrow(new RuntimeException("Profile not found"));

        ResponseEntity<Profile> response = profileController.updateCandidateProfile(1L, request);

        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
    }

    @Test
    void shouldCreateCompanyProfileUsingJwtClaimsToResolveUser() {
        CompanyProfileRequest request = new CompanyProfileRequest();
        request.setCompanyName("Acme");
        request.setCompanyDescription("A company description long enough");
        request.setNationality("Colombia");

        Profile profile = Profile.builder().id(30L).professionalTitle("Acme").build();

        when(profileService.resolveEffectiveUserId(1L, "acme@example.com", "google-company", "Acme Admin", "https://avatar", Role.COMPANY))
                .thenReturn(88L);
        when(profileService.upsertCompanyProfile(eq(88L), any(CompanyProfileRequest.class))).thenReturn(profile);

        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(jwtToken("1", "acme@example.com", "Acme Admin", "google-company", "https://avatar"), null));

        ResponseEntity<Profile> response = profileController.createCompanyProfile(1L, request);

        assertEquals(HttpStatus.CREATED, response.getStatusCode());
        assertEquals(30L, response.getBody().getId());
        verify(profileService, times(1))
                .resolveEffectiveUserId(1L, "acme@example.com", "google-company", "Acme Admin", "https://avatar", Role.COMPANY);
        verify(profileService, times(1)).upsertCompanyProfile(eq(88L), any(CompanyProfileRequest.class));
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
        void shouldUpdateCompanyProfileUsingPathUserIdWhenPrincipalIsNotJwt() {
                CompanyProfileRequest request = new CompanyProfileRequest();
                request.setCompanyName("Acme");
                request.setCompanyDescription("Description long enough");
                request.setNationality("Colombia");

                Profile profile = Profile.builder().id(23L).professionalTitle("Acme").build();
                when(profileService.upsertCompanyProfile(eq(1L), any(CompanyProfileRequest.class))).thenReturn(profile);

                SecurityContextHolder.getContext().setAuthentication(
                                new UsernamePasswordAuthenticationToken("plain-principal", null));

                ResponseEntity<Profile> response = profileController.updateCompanyProfile(1L, request);

                assertEquals(HttpStatus.OK, response.getStatusCode());
                assertEquals(23L, response.getBody().getId());
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

        @Test
        void shouldReturn404WhenUpdatingCompanyProfileFails() {
                CompanyProfileRequest request = new CompanyProfileRequest();
                request.setCompanyName("Acme");
                request.setCompanyDescription("Description long enough");
                request.setNationality("Colombia");

                when(profileService.upsertCompanyProfile(eq(1L), any(CompanyProfileRequest.class)))
                                .thenThrow(new RuntimeException("Profile not found"));

                ResponseEntity<Profile> response = profileController.updateCompanyProfile(1L, request);

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

