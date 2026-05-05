package ieti.jobswipe.service.profile;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import org.mockito.ArgumentCaptor;
import org.springframework.test.web.client.MockRestServiceServer;

import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import org.springframework.http.MediaType;

import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import ieti.jobswipe.model.entity.CandidateProfile;
import ieti.jobswipe.model.entity.Profile;
import ieti.jobswipe.model.entity.ProfileFeedback;
import ieti.jobswipe.model.entity.User;
import ieti.jobswipe.model.Role;
import ieti.jobswipe.repository.profile.ProfileFeedbackRepository;
import ieti.jobswipe.repository.profile.ProfileRepository;

@ExtendWith(MockitoExtension.class)
class ProfileFeedbackServiceTest {

    @Mock
    private ProfileFeedbackRepository feedbackRepository;

    @Mock
    private ProfileRepository profileRepository;

    @Mock
    private PlatformTransactionManager transactionManager;

    private ProfileFeedbackService profileFeedbackService;

    private User testUser;
    private Profile testProfile;
    private CandidateProfile testCandidateProfile;

    @BeforeEach
    void setUp() {
        // Create service with real dependencies (simplified for testing)
        profileFeedbackService = new ProfileFeedbackService(
                feedbackRepository,
                profileRepository,
                transactionManager,
                "http://localhost:8001");

        testUser = User.builder()
                .id(1L)
                .name("John Doe")
                .email("john@example.com")
                .role(Role.CANDIDATE)
                .build();

        testCandidateProfile = CandidateProfile.builder()
                .id(1L)
                .languages("English, Spanish")
                .expectedSalary(5000.0)
                .availability("Immediate")
                .githubUrl("https://github.com/johndoe")
                .linkedinUrl("https://linkedin.com/in/johndoe")
                .build();

        testProfile = Profile.builder()
                .id(100L)
                .user(testUser)
                .candidateProfile(testCandidateProfile)
                .professionalTitle("Backend Developer")
                .summary("Java developer with 5 years experience")
                .skills("[\"Java\", \"Spring\"]")
                .experience("[{\"company\": \"Acme\"}]")
                .education("Systems Engineering")
                .location("Bogota")
                .nationality("Colombian")
                .phoneNumber("3000000000")
                .build();
    }

    @Test
    void shouldBuildPayloadWithCandidateProfileFields() throws Exception {
        Map<String, Object> payload = invokeBuildPayload(testProfile);

        assertEquals(100L, payload.get("profileId"));
        assertEquals("John Doe", payload.get("displayName"));
        assertEquals("Backend Developer", payload.get("professionalTitle"));
        assertEquals("Java developer with 5 years experience", payload.get("summary"));
        assertEquals("[\"Java\", \"Spring\"]", payload.get("skills"));
        assertEquals("[{\"company\": \"Acme\"}]", payload.get("experience"));
        assertEquals("Systems Engineering", payload.get("education"));
        assertEquals("Bogota", payload.get("location"));
        assertEquals("Colombian", payload.get("nationality"));
        assertEquals("English, Spanish", payload.get("languages"));
        assertEquals(5000.0, payload.get("expectedSalary"));
        assertEquals("Immediate", payload.get("availability"));
        assertEquals("john@example.com", payload.get("email"));
        assertEquals("3000000000", payload.get("phoneNumber"));
        assertEquals("https://github.com/johndoe", payload.get("github"));
        assertEquals("https://linkedin.com/in/johndoe", payload.get("linkedin"));
    }

    @Test
    void shouldBuildPayloadWithNullCandidateProfileFields() throws Exception {
        Profile profile = Profile.builder()
                .id(101L)
                .user(testUser)
                .candidateProfile(null)
                .professionalTitle("Company Admin")
                .location("Medellin")
                .phoneNumber("3111111111")
                .build();

        Map<String, Object> payload = invokeBuildPayload(profile);

        assertEquals(101L, payload.get("profileId"));
        assertEquals("john@example.com", payload.get("email"));
        assertNull(payload.get("languages"));
        assertNull(payload.get("expectedSalary"));
        assertNull(payload.get("availability"));
        assertNull(payload.get("github"));
        assertNull(payload.get("linkedin"));
    }

    @Test
    void shouldBuildPayloadWithNullUserFields() throws Exception {
        Profile profile = Profile.builder()
                .id(102L)
                .user(null)
                .candidateProfile(testCandidateProfile)
                .professionalTitle("Freelancer")
                .location("Remote")
                .build();

        Map<String, Object> payload = invokeBuildPayload(profile);

        assertEquals(102L, payload.get("profileId"));
        assertNull(payload.get("displayName"));
        assertNull(payload.get("email"));
        assertEquals("English, Spanish", payload.get("languages"));
    }

    @Test
    void shouldDispatchAnalyzeAndSaveAsync() {
        // Verify that async method doesn't throw exception
        assertDoesNotThrow(() -> profileFeedbackService.analyzeAndSaveAsync(100L));
    }

    @Test
    void shouldAnalyzeAndSaveFeedbackSuccessfully() throws Exception {
        RestTemplate restTemplate = (RestTemplate) ReflectionTestUtils.getField(profileFeedbackService, "restTemplate");
        MockRestServiceServer server = MockRestServiceServer.bindTo(restTemplate).build();

        when(profileRepository.findByIdForAnalysis(100L)).thenReturn(Optional.of(testProfile));
        when(profileRepository.findById(100L)).thenReturn(Optional.of(testProfile));
        when(feedbackRepository.findByProfile(testProfile)).thenReturn(Optional.empty());
        when(feedbackRepository.save(any(ProfileFeedback.class))).thenAnswer(invocation -> invocation.getArgument(0));

        server.expect(requestTo("http://localhost:8001/profiles/analyze"))
                .andRespond(withSuccess("{\"status\":\"ok\"}", MediaType.APPLICATION_JSON));

        assertDoesNotThrow(() -> profileFeedbackService.analyzeAndSave(100L));

        ArgumentCaptor<ProfileFeedback> captor = ArgumentCaptor.forClass(ProfileFeedback.class);
        verify(feedbackRepository, times(1)).save(captor.capture());
        assertEquals("{\"status\":\"ok\"}", captor.getValue().getPayload());
        server.verify();
    }

    @Test
    void shouldSkipSavingWhenProfileIsMissingDuringAnalysis() {
        RestTemplate restTemplate = (RestTemplate) ReflectionTestUtils.getField(profileFeedbackService, "restTemplate");
        MockRestServiceServer server = MockRestServiceServer.bindTo(restTemplate).build();

        when(profileRepository.findByIdForAnalysis(999L)).thenReturn(Optional.empty());

        assertDoesNotThrow(() -> profileFeedbackService.analyzeAndSave(999L));

        verifyNoInteractions(feedbackRepository);
        server.verify();
    }

    @Test
    void shouldSkipSavingWhenAiServiceReturnsError() throws Exception {
        RestTemplate restTemplate = (RestTemplate) ReflectionTestUtils.getField(profileFeedbackService, "restTemplate");
        MockRestServiceServer server = MockRestServiceServer.bindTo(restTemplate).build();

        when(profileRepository.findByIdForAnalysis(100L)).thenReturn(Optional.of(testProfile));

        server.expect(requestTo("http://localhost:8001/profiles/analyze"))
                .andRespond(withServerError());

        assertDoesNotThrow(() -> profileFeedbackService.analyzeAndSave(100L));

        verify(feedbackRepository, never()).save(any(ProfileFeedback.class));
        server.verify();
    }

    @Test
    void shouldBuildPayloadWithEmptyStrings() throws Exception {
        Profile profile = Profile.builder()
                .id(104L)
                .skills("")
                .experience("")
                .education("")
                .location("")
                .nationality("")
                .phoneNumber("")
                .build();

        Map<String, Object> payload = invokeBuildPayload(profile);

        assertEquals(104L, payload.get("profileId"));
        assertEquals("", payload.get("skills"));
        assertEquals("", payload.get("experience"));
        assertEquals("", payload.get("education"));
        assertEquals("", payload.get("location"));
        assertEquals("", payload.get("nationality"));
        assertEquals("", payload.get("phoneNumber"));
    }

    @Test
    void shouldBuildPayloadWithSpecialCharacters() throws Exception {
        Profile profile = Profile.builder()
                .id(105L)
                .user(testUser)
                .candidateProfile(testCandidateProfile)
                .summary("Developer with <skills> & \"experience\"")
                .skills("[\"C++\", \"C#\"]")
                .build();

        Map<String, Object> payload = invokeBuildPayload(profile);

        assertEquals(105L, payload.get("profileId"));
        assertTrue(payload.get("summary").toString().contains("&"));
        assertTrue(payload.get("skills").toString().contains("C++"));
    }

    @Test
    void shouldBuildPayloadWithAllNullFields() throws Exception {
        Profile profile = Profile.builder()
                .id(103L)
                .build();

        Map<String, Object> payload = invokeBuildPayload(profile);

        assertEquals(103L, payload.get("profileId"));
        assertNull(payload.get("displayName"));
        assertNull(payload.get("professionalTitle"));
        assertNull(payload.get("summary"));
        assertNull(payload.get("skills"));
        assertNull(payload.get("experience"));
        assertNull(payload.get("education"));
        assertNull(payload.get("location"));
        assertNull(payload.get("nationality"));
        assertNull(payload.get("languages"));
        assertNull(payload.get("expectedSalary"));
        assertNull(payload.get("availability"));
        assertNull(payload.get("email"));
        assertNull(payload.get("phoneNumber"));
        assertNull(payload.get("github"));
        assertNull(payload.get("linkedin"));
    }

    // Helper method to invoke private buildPayload via reflection
    @SuppressWarnings("unchecked")
    private Map<String, Object> invokeBuildPayload(Profile profile) throws Exception {
        Method method = ProfileFeedbackService.class.getDeclaredMethod("buildPayload", Profile.class);
        method.setAccessible(true);
        return (Map<String, Object>) method.invoke(profileFeedbackService, profile);
    }
}
