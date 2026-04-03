package ieti.JobSwipe.service;

import ieti.JobSwipe.dto.CandidateProfileRequest;
import ieti.JobSwipe.dto.CompanyProfileRequest;
import ieti.JobSwipe.model.CandidateProfile;
import ieti.JobSwipe.model.CompanyProfile;
import ieti.JobSwipe.model.Profile;
import ieti.JobSwipe.model.Role;
import ieti.JobSwipe.model.User;
import ieti.JobSwipe.repository.CandidateProfileRepository;
import ieti.JobSwipe.repository.CompanyProfileRepository;
import ieti.JobSwipe.repository.ProfileRepository;
import ieti.JobSwipe.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProfileServiceTest {

    @Mock
    private ProfileRepository profileRepository;

    @Mock
    private CandidateProfileRepository candidateProfileRepository;

    @Mock
    private CompanyProfileRepository companyProfileRepository;

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private ProfileService profileService;

    private User testUser;

    @BeforeEach
    void setUp() {
        testUser = User.builder()
                .id(1L)
                .name("John Doe")
                .email("john@example.com")
                .role(Role.CANDIDATE)
                .build();
    }

    @Test
    void shouldUpsertCandidateProfileAndSetCandidateRole() {
        CandidateProfileRequest request = new CandidateProfileRequest();
        request.setProfessionalTitle("Backend Developer");
        request.setSummary("Java developer");
        request.setSkills("Java, Spring");
        request.setExperience("3 years");
        request.setEducation("Systems Engineer");
        request.setLocation("Bogota");
        request.setPhoneNumber("3000000000");
        request.setLanguages("English, Spanish");
        request.setExpectedSalary(5000.0);

        when(userRepository.findById(1L)).thenReturn(Optional.of(testUser));
        when(profileRepository.findByUserId(1L)).thenReturn(Optional.empty());
        when(profileRepository.save(any(Profile.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(candidateProfileRepository.save(any(CandidateProfile.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Profile profile = profileService.upsertCandidateProfile(1L, request);

        assertNotNull(profile);
        assertEquals("Backend Developer", profile.getProfessionalTitle());
        assertEquals("Java developer", profile.getSummary());
        assertEquals("3000000000", profile.getPhoneNumber());
        assertEquals(true, profile.getOnboardingCompleted());
        assertEquals(Role.CANDIDATE, testUser.getRole());
        assertNotNull(profile.getCandidateProfile());
        assertEquals("English, Spanish", profile.getCandidateProfile().getLanguages());

        verify(userRepository, times(1)).save(testUser);
        verify(profileRepository, times(2)).save(any(Profile.class));
        verify(candidateProfileRepository, times(1)).save(any(CandidateProfile.class));
    }

    @Test
    void shouldUpsertCompanyProfileAndSetCompanyRole() {
        CompanyProfileRequest request = new CompanyProfileRequest();
        request.setCompanyName("Acme SAS");
        request.setCompanyDescription("Tech company");
        request.setIndustry("Software");
        request.setCompanySize("51-200");
        request.setHeadquartersLocation("Medellin");
        request.setPhoneNumber("3111111111");

        when(userRepository.findById(1L)).thenReturn(Optional.of(testUser));
        when(profileRepository.findByUserId(1L)).thenReturn(Optional.empty());
        when(profileRepository.save(any(Profile.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(companyProfileRepository.save(any(CompanyProfile.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Profile profile = profileService.upsertCompanyProfile(1L, request);

        assertNotNull(profile);
        assertEquals("Acme SAS", profile.getProfessionalTitle());
        assertEquals("Tech company", profile.getSummary());
        assertEquals("3111111111", profile.getPhoneNumber());
        assertEquals(true, profile.getOnboardingCompleted());
        assertEquals(Role.COMPANY, testUser.getRole());
        assertNotNull(profile.getCompanyProfile());
        assertEquals("Software", profile.getCompanyProfile().getIndustry());

        verify(userRepository, times(1)).save(testUser);
        verify(profileRepository, times(2)).save(any(Profile.class));
        verify(companyProfileRepository, times(1)).save(any(CompanyProfile.class));
    }

    @Test
    void shouldThrowWhenProfileNotFoundByUserId() {
        when(profileRepository.findByUserId(99L)).thenReturn(Optional.empty());

        RuntimeException exception = assertThrows(RuntimeException.class, () -> profileService.getProfileByUserId(99L));

        assertEquals("Profile not found", exception.getMessage());
        verify(profileRepository, times(1)).findByUserId(99L);
    }
}
