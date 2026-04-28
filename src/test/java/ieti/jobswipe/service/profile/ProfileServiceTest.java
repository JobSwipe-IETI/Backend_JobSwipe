package ieti.jobswipe.service.profile;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import ieti.jobswipe.dto.company.CompanyProfileRequest;
import ieti.jobswipe.dto.profile.CandidateExperienceRequest;
import ieti.jobswipe.dto.profile.CandidateProfileRequest;
import ieti.jobswipe.exception.UserNotFoundException;
import ieti.jobswipe.model.entity.CandidateProfile;
import ieti.jobswipe.model.entity.CompanyProfile;
import ieti.jobswipe.model.entity.Profile;
import ieti.jobswipe.model.Role;
import ieti.jobswipe.model.entity.User;
import ieti.jobswipe.repository.profile.CandidateProfileRepository;
import ieti.jobswipe.repository.company.CompanyProfileRepository;
import ieti.jobswipe.repository.profile.ProfileRepository;
import ieti.jobswipe.repository.user.UserRepository;
    


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
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.spy;

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
        request.setDisplayName("John Doe");
        request.setProfessionalTitle("Backend Developer");
        request.setSummary("Java developer");
        request.setSkills(java.util.List.of("Java", "Spring"));
        CandidateExperienceRequest experience = new CandidateExperienceRequest();
        experience.setTitle("Backend Developer");
        experience.setCompany("Acme");
        experience.setStartDate("2022-01");
        experience.setEndDate("2024-01");
        experience.setCurrent(false);
        request.setExperiences(java.util.List.of(experience));
        request.setEducation("Systems Engineer");
        request.setLocation("Bogota");
        request.setPhoneNumber("3000000000");
        request.setLanguages("English, Spanish");
        request.setExpectedSalary(5000.0);
        request.setGithubUrl("https://github.com/johndoe");
        request.setLinkedinUrl("https://linkedin.com/in/johndoe");

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
        assertEquals("John Doe", testUser.getName());
        assertEquals(Role.CANDIDATE, testUser.getRole());
        assertNotNull(profile.getCandidateProfile());
        assertEquals("English, Spanish", profile.getCandidateProfile().getLanguages());
        assertEquals("[\"Java\",\"Spring\"]", profile.getSkills());
        assertEquals("https://github.com/johndoe", profile.getCandidateProfile().getGithubUrl());
        assertEquals("https://linkedin.com/in/johndoe", profile.getCandidateProfile().getLinkedinUrl());

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

    @Test
    void shouldReturnProfileAndInitializeLobsWhenFoundByUserId() {
        Profile profile = Profile.builder()
                .id(2L)
                .skills("[\"Java\"]")
                .experience("[{\"company\":\"Acme\"}]")
                .education("Systems Engineering")
                .build();

        when(profileRepository.findByUserId(2L)).thenReturn(Optional.of(profile));

        Profile result = profileService.getProfileByUserId(2L);

        assertEquals(2L, result.getId());
        assertEquals("[\"Java\"]", result.getSkills());
        assertEquals("[{\"company\":\"Acme\"}]", result.getExperience());
        assertEquals("Systems Engineering", result.getEducation());
    }

    @Test
    void shouldReturnProfileWhenLobFieldsAreNull() {
        Profile profile = Profile.builder()
                .id(21L)
                .skills(null)
                .experience(null)
                .education(null)
                .build();

        when(profileRepository.findByUserId(21L)).thenReturn(Optional.of(profile));

        Profile result = profileService.getProfileByUserId(21L);

        assertEquals(21L, result.getId());
        assertNull(result.getSkills());
        assertNull(result.getExperience());
        assertNull(result.getEducation());
    }

    @Test
    void shouldDeleteExistingCompanyProfileWhenUpsertingCandidateProfile() {
        CandidateProfileRequest request = new CandidateProfileRequest();
        request.setDisplayName("John Doe");
        request.setProfessionalTitle("Backend Developer");
        request.setSummary("Java developer");
        request.setNationality("Colombia");

        CompanyProfile existingCompanyProfile = CompanyProfile.builder().id(50L).companyName("Acme").build();
        Profile existingProfile = Profile.builder()
                .id(10L)
                .user(testUser)
                .companyProfile(existingCompanyProfile)
                .build();

        when(userRepository.findById(1L)).thenReturn(Optional.of(testUser));
        when(profileRepository.findByUserId(1L)).thenReturn(Optional.of(existingProfile));
        when(profileRepository.save(any(Profile.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(candidateProfileRepository.save(any(CandidateProfile.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Profile result = profileService.upsertCandidateProfile(1L, request);

        assertNotNull(result.getCandidateProfile());
        assertNull(result.getCompanyProfile());
        verify(companyProfileRepository, times(1)).delete(existingCompanyProfile);
    }

    @Test
    void shouldDeleteExistingCandidateProfileWhenUpsertingCompanyProfile() {
        CompanyProfileRequest request = new CompanyProfileRequest();
        request.setCompanyName("Acme SAS");
        request.setCompanyDescription("Tech company");
        request.setNationality("Colombia");

        CandidateProfile existingCandidateProfile = CandidateProfile.builder().id(60L).languages("English").build();
        Profile existingProfile = Profile.builder()
                .id(10L)
                .user(testUser)
                .candidateProfile(existingCandidateProfile)
                .build();

        when(userRepository.findById(1L)).thenReturn(Optional.of(testUser));
        when(profileRepository.findByUserId(1L)).thenReturn(Optional.of(existingProfile));
        when(profileRepository.save(any(Profile.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(companyProfileRepository.save(any(CompanyProfile.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Profile result = profileService.upsertCompanyProfile(1L, request);

        assertNotNull(result.getCompanyProfile());
        assertNull(result.getCandidateProfile());
        verify(candidateProfileRepository, times(1)).delete(existingCandidateProfile);
    }

    @Test
    void shouldResolveRequestedUserIdWhenItExists() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(testUser));

        Long effectiveUserId = profileService.resolveEffectiveUserId(1L, "john@example.com", "google-123", "John", null, Role.CANDIDATE);

        assertEquals(1L, effectiveUserId);
        verify(userRepository, never()).findByGoogleId(any());
        verify(userRepository, never()).findByEmail(any());
    }

    @Test
    void shouldResolveByGoogleIdWhenRequestedUserIdIsMissing() {
        User googleUser = User.builder().id(2L).googleId("google-123").build();

        when(userRepository.findById(99L)).thenReturn(Optional.empty());
        when(userRepository.findByGoogleId("google-123")).thenReturn(Optional.of(googleUser));

        Long effectiveUserId = profileService.resolveEffectiveUserId(99L, "john@example.com", "google-123", "John", null, Role.CANDIDATE);

        assertEquals(2L, effectiveUserId);
        verify(userRepository, never()).findByEmail(any());
    }

    @Test
    void shouldResolveByEmailWhenGoogleIdIsMissing() {
        User emailUser = User.builder().id(3L).email("john@example.com").build();

        when(userRepository.findById(99L)).thenReturn(Optional.empty());
        when(userRepository.findByEmail("john@example.com")).thenReturn(Optional.of(emailUser));

        Long effectiveUserId = profileService.resolveEffectiveUserId(99L, "john@example.com", null, "John", null, Role.CANDIDATE);

        assertEquals(3L, effectiveUserId);
    }

    @Test
    void shouldCreateUserWhenNoExistingUserMatchesJwtClaims() {
        User recreatedUser = User.builder()
                .id(4L)
                .name("John")
                .email("john@example.com")
                .googleId("google-123")
                .avatarUrl("https://avatar")
                .role(Role.COMPANY)
                .build();

        when(userRepository.findById(99L)).thenReturn(Optional.empty());
        when(userRepository.findByGoogleId("google-123")).thenReturn(Optional.empty());
        when(userRepository.findByEmail("john@example.com")).thenReturn(Optional.empty());
        when(userRepository.save(any(User.class))).thenReturn(recreatedUser);

        Long effectiveUserId = profileService.resolveEffectiveUserId(99L, "john@example.com", "google-123", "John", "https://avatar", Role.COMPANY);

        assertEquals(4L, effectiveUserId);
        verify(userRepository, times(1)).save(any(User.class));
    }

    @Test
    void shouldThrowWhenEmailIsMissingAndNoUserCanBeResolved() {
        when(userRepository.findById(99L)).thenReturn(Optional.empty());

        UserNotFoundException exception = assertThrows(UserNotFoundException.class,
                () -> profileService.resolveEffectiveUserId(99L, " ", null, "John", null, Role.CANDIDATE));

        assertEquals("User not found", exception.getMessage());
        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    void shouldThrowWhenUpsertingCandidateProfileAndUserDoesNotExist() {
        CandidateProfileRequest request = new CandidateProfileRequest();
        request.setDisplayName("John Doe");
        request.setProfessionalTitle("Backend Developer");
        request.setSummary("Java developer");
        request.setNationality("Colombia");

        when(userRepository.findById(404L)).thenReturn(Optional.empty());

        RuntimeException exception = assertThrows(RuntimeException.class,
                () -> profileService.upsertCandidateProfile(404L, request));

        assertEquals("User not found", exception.getMessage());
        verify(profileRepository, never()).save(any(Profile.class));
    }

    @Test
    void shouldThrowWhenUpsertingCompanyProfileAndUserDoesNotExist() {
        CompanyProfileRequest request = new CompanyProfileRequest();
        request.setCompanyName("Acme");
        request.setCompanyDescription("Tech company");
        request.setNationality("Colombia");

        when(userRepository.findById(405L)).thenReturn(Optional.empty());

        RuntimeException exception = assertThrows(RuntimeException.class,
                () -> profileService.upsertCompanyProfile(405L, request));

        assertEquals("User not found", exception.getMessage());
        verify(profileRepository, never()).save(any(Profile.class));
    }

    @Test
    void shouldReturnHasProfileFlagFromRepository() {
        when(profileRepository.existsByUserId(77L)).thenReturn(true);

        boolean result = profileService.hasProfileByUserId(77L);

        assertEquals(true, result);
        verify(profileRepository, times(1)).existsByUserId(77L);
    }

    @Test
    void shouldCreateUserWithNullGoogleIdWhenClaimIsBlank() {
        User recreatedUser = User.builder()
                .id(10L)
                .name("john@example.com")
                .email("john@example.com")
                .googleId(null)
                .avatarUrl("https://avatar")
                .role(Role.CANDIDATE)
                .build();

        when(userRepository.findByEmail("john@example.com")).thenReturn(Optional.empty());
        when(userRepository.save(any(User.class))).thenReturn(recreatedUser);

        Long effectiveUserId = profileService.resolveEffectiveUserId(
                null,
                "john@example.com",
                "   ",
                "   ",
                "https://avatar",
                Role.CANDIDATE);

        assertEquals(10L, effectiveUserId);
        verify(userRepository, never()).findByGoogleId(any());
        verify(userRepository, times(1)).findByEmail("john@example.com");
        verify(userRepository, times(1)).save(any(User.class));
    }

        @Test
        void shouldResolveByEmailWhenRequestedUserIdIsNullAndGoogleIdBlank() {
        User emailUser = User.builder().id(8L).email("john@example.com").build();

        when(userRepository.findByEmail("john@example.com")).thenReturn(Optional.of(emailUser));

        Long effectiveUserId = profileService.resolveEffectiveUserId(
            null,
            "john@example.com",
            "  ",
            "John",
            null,
            Role.CANDIDATE);

        assertEquals(8L, effectiveUserId);
        verify(userRepository, never()).findById(any());
        verify(userRepository, never()).findByGoogleId(any());
        verify(userRepository, times(1)).findByEmail("john@example.com");
        }

        @Test
        void shouldCreateUserUsingEmailAsNameWhenNameIsBlank() {
        User recreatedUser = User.builder()
            .id(9L)
            .name("john@example.com")
            .email("john@example.com")
            .googleId(null)
            .avatarUrl(null)
            .role(Role.CANDIDATE)
            .build();

        when(userRepository.findByGoogleId("google-blank")).thenReturn(Optional.empty());
        when(userRepository.findByEmail("john@example.com")).thenReturn(Optional.empty());
        when(userRepository.save(any(User.class))).thenReturn(recreatedUser);

        Long effectiveUserId = profileService.resolveEffectiveUserId(
            null,
            "john@example.com",
            "google-blank",
            "   ",
            "   ",
            Role.CANDIDATE);

        assertEquals(9L, effectiveUserId);
        verify(userRepository, times(1)).save(any(User.class));
        }

        @Test
        void shouldReuseExistingCandidateProfileWhenUpsertingCandidate() {
        CandidateProfileRequest request = new CandidateProfileRequest();
        request.setDisplayName("John Doe");
        request.setProfessionalTitle("Backend Developer");
        request.setSummary("Java developer");
        request.setNationality("Colombia");
        request.setSector("Technology");

        CandidateProfile existingCandidate = CandidateProfile.builder()
            .id(70L)
            .languages("English")
            .build();
        Profile existingProfile = Profile.builder()
            .id(11L)
            .user(testUser)
            .candidateProfile(existingCandidate)
            .build();

        when(userRepository.findById(1L)).thenReturn(Optional.of(testUser));
        when(profileRepository.findByUserId(1L)).thenReturn(Optional.of(existingProfile));
        when(profileRepository.save(any(Profile.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(candidateProfileRepository.save(any(CandidateProfile.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Profile result = profileService.upsertCandidateProfile(1L, request);

        assertNotNull(result.getCandidateProfile());
        assertEquals(70L, result.getCandidateProfile().getId());
        verify(candidateProfileRepository, never()).delete(any());
        }

        @Test
        void shouldReuseExistingCompanyProfileWhenUpsertingCompany() {
        CompanyProfileRequest request = new CompanyProfileRequest();
        request.setCompanyName("Acme SAS");
        request.setCompanyDescription("Tech company");
        request.setNationality("Colombia");

        CompanyProfile existingCompany = CompanyProfile.builder()
            .id(80L)
            .companyName("Old Co")
            .build();
        Profile existingProfile = Profile.builder()
            .id(12L)
            .user(testUser)
            .companyProfile(existingCompany)
            .build();

        when(userRepository.findById(1L)).thenReturn(Optional.of(testUser));
        when(profileRepository.findByUserId(1L)).thenReturn(Optional.of(existingProfile));
        when(profileRepository.save(any(Profile.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(companyProfileRepository.save(any(CompanyProfile.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Profile result = profileService.upsertCompanyProfile(1L, request);

        assertNotNull(result.getCompanyProfile());
        assertEquals(80L, result.getCompanyProfile().getId());
        verify(companyProfileRepository, never()).delete(any());
        }

        @Test
        void shouldThrowIllegalArgumentWhenSkillsSerializationFails() throws Exception {
        ProfileService serviceSpy = spy(profileService);
        ObjectMapper failingMapper = org.mockito.Mockito.mock(ObjectMapper.class);
        doThrow(new JsonProcessingException("boom") { }).when(failingMapper).writeValueAsString(any());

        java.lang.reflect.Field objectMapperField = ProfileService.class.getDeclaredField("objectMapper");
        objectMapperField.setAccessible(true);
        objectMapperField.set(serviceSpy, failingMapper);

        CandidateProfileRequest request = new CandidateProfileRequest();
        request.setDisplayName("John Doe");
        request.setProfessionalTitle("Backend Developer");
        request.setSummary("Java developer");
        request.setNationality("Colombia");
        request.setSkills(java.util.List.of("Java"));

        when(userRepository.findById(1L)).thenReturn(Optional.of(testUser));
        when(profileRepository.findByUserId(1L)).thenReturn(Optional.empty());

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
            () -> serviceSpy.upsertCandidateProfile(1L, request));

        assertEquals("Unable to serialize profile payload", exception.getMessage());
        }
}

