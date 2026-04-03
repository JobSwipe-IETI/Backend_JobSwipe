package ieti.JobSwipe.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import ieti.JobSwipe.dto.CandidateExperienceRequest;
import ieti.JobSwipe.dto.CandidateProfileRequest;
import ieti.JobSwipe.dto.CompanyProfileRequest;
import ieti.JobSwipe.model.Profile;
import ieti.JobSwipe.service.ProfileService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
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
        request.setSummary("Summary");
                request.setSkills(java.util.List.of("Java", "Spring"));
                CandidateExperienceRequest experience = new CandidateExperienceRequest();
                experience.setTitle("Backend Developer");
                experience.setCompany("Acme");
                experience.setStartDate("2022-01");
                request.setExperiences(java.util.List.of(experience));

        Profile profile = Profile.builder()
                .id(1L)
                .professionalTitle("Backend Developer")
                .summary("Summary")
                .onboardingCompleted(true)
                .build();

        when(profileService.upsertCandidateProfile(eq(1L), any(CandidateProfileRequest.class))).thenReturn(profile);

        mockMvc.perform(post("/profiles/candidate/1")
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
        request.setCompanyDescription("Desc");

        Profile profile = Profile.builder()
                .id(1L)
                .professionalTitle("Acme SAS")
                .summary("Desc")
                .onboardingCompleted(true)
                .build();

        when(profileService.upsertCompanyProfile(eq(1L), any(CompanyProfileRequest.class))).thenReturn(profile);

        mockMvc.perform(put("/profiles/company/1")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.professionalTitle", is("Acme SAS")));

        verify(profileService, times(1)).upsertCompanyProfile(eq(1L), any(CompanyProfileRequest.class));
    }
}
