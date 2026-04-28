package ieti.jobswipe.service.matching;

import ieti.jobswipe.dto.matching.MatchingResponse;
import ieti.jobswipe.exception.ProfileNotFoundException;
import ieti.jobswipe.exception.VacancyNotFoundException;
import ieti.jobswipe.model.entity.CandidateProfile;
import ieti.jobswipe.model.ExperienceLevel;
import ieti.jobswipe.model.entity.Profile;
import ieti.jobswipe.model.entity.Vacancy;
import ieti.jobswipe.repository.profile.ProfileRepository;
import ieti.jobswipe.repository.vacancy.VacancyRepository;
       

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpEntity;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MatchingServiceTest {

    @Mock
    private ProfileRepository profileRepository;

    @Mock
    private VacancyRepository vacancyRepository;

    @Mock
    private RestTemplate restTemplate;

    private MatchingService matchingService;

    @BeforeEach
    void setUp() throws Exception {
        matchingService = new MatchingService(profileRepository, vacancyRepository, restTemplate);
        java.lang.reflect.Field baseUrlField = MatchingService.class.getDeclaredField("aiServiceBaseUrl");
        baseUrlField.setAccessible(true);
        baseUrlField.set(matchingService, "http://ai-service");
    }

    @Test
    void shouldCalculateMatchSuccessfullyAndSendExpectedPayload() {
        CandidateProfile candidateProfile = CandidateProfile.builder()
                .sector("Technology")
                .expectedSalary(7500.0)
                .availability("Immediate")
                .languages("English, Spanish")
                .build();

        Profile profile = Profile.builder()
                .id(1L)
                .professionalTitle("Backend Engineer")
                .summary("5 years building APIs")
                .skills("Java, Spring")
                .experience("Senior experience")
                .education("Computer Science")
                .location("Bogota")
                .candidateProfile(candidateProfile)
                .build();

        Vacancy vacancy = Vacancy.builder()
                .id(10L)
                .title("Senior Java Developer")
                .description("Design and build backend systems")
                .technologies(List.of("Java", "Spring Boot"))
                .softSkills(List.of("Communication"))
                .technicalRequirements(List.of("Microservices"))
                .location("Bogota")
                .minSalary(6000.0)
                .maxSalary(9000.0)
                .experienceLevel(ExperienceLevel.SENIOR)
                .sector("Technology")
                .build();

        Map<String, Object> aiResponse = Map.of(
                "similarity_score", 0.91,
                "compatibility_percentage", 88.0f,
                "compatibility_level", "high",
                "feedback", "Great fit",
                "used_llm_feedback", true);

        when(profileRepository.findByUserId(1L)).thenReturn(Optional.of(profile));
        when(vacancyRepository.findById(10L)).thenReturn(Optional.of(vacancy));
        when(restTemplate.postForObject(eq("http://ai-service/embeddings/match"), any(HttpEntity.class), eq(Map.class)))
                .thenReturn(aiResponse);

        MatchingResponse result = matchingService.calculateMatch(1L, 10L);

        assertNotNull(result);
        assertEquals(0.91, result.getSimilarityScore());
        assertEquals(88.0f, result.getCompatibilityPercentage());
        assertEquals("high", result.getCompatibilityLevel());
        assertEquals("Great fit", result.getFeedback());
        assertEquals(true, result.getUsedLlmFeedback());

        @SuppressWarnings("rawtypes")
        ArgumentCaptor<HttpEntity> requestCaptor = ArgumentCaptor.forClass(HttpEntity.class);
        verify(restTemplate, times(1))
                .postForObject(eq("http://ai-service/embeddings/match"), requestCaptor.capture(), eq(Map.class));

        @SuppressWarnings("unchecked")
        Map<String, String> payload = (Map<String, String>) requestCaptor.getValue().getBody();
        assertNotNull(payload);
        assertTrue(payload.get("candidate_text").contains("Professional Title: Backend Engineer"));
        assertTrue(payload.get("candidate_text").contains("Sector: Technology"));
        assertTrue(payload.get("vacancy_text").contains("Technologies: Java, Spring Boot"));
        assertTrue(payload.get("vacancy_text").contains("Salary Range: 6000.0 - 9000.0"));
    }

    @Test
    void shouldThrowProfileNotFoundWhenCandidateProfileDoesNotExist() {
        when(profileRepository.findByUserId(999L)).thenReturn(Optional.empty());

                ProfileNotFoundException exception = assertThrows(ProfileNotFoundException.class,
                                () -> matchingService.calculateMatch(999L, 10L));
                assertEquals("Profile not found", exception.getMessage());

        verify(vacancyRepository, never()).findById(any());
        verify(restTemplate, never()).postForObject(any(String.class), any(), eq(Map.class));
    }

    @Test
    void shouldThrowVacancyNotFoundWhenVacancyDoesNotExist() {
        Profile profile = Profile.builder().id(1L).build();
        when(profileRepository.findByUserId(1L)).thenReturn(Optional.of(profile));
        when(vacancyRepository.findById(999L)).thenReturn(Optional.empty());

                VacancyNotFoundException exception = assertThrows(VacancyNotFoundException.class,
                                () -> matchingService.calculateMatch(1L, 999L));
                assertEquals("Vacancy not found", exception.getMessage());

        verify(restTemplate, never()).postForObject(any(String.class), any(), eq(Map.class));
    }

    @Test
    void shouldUseFallbackTextsWhenCandidateAndVacancyDataAreMissing() {
        Profile profile = Profile.builder().id(1L).build();
        Vacancy vacancy = Vacancy.builder()
                .id(10L)
                .technologies(null)
                .softSkills(null)
                .technicalRequirements(null)
                .build();

        Map<String, Object> aiResponse = Map.of(
                "similarity_score", 0.2,
                "compatibility_percentage", 20.0f,
                "compatibility_level", "low",
                "feedback", "Not a fit",
                "used_llm_feedback", false);

        when(profileRepository.findByUserId(1L)).thenReturn(Optional.of(profile));
        when(vacancyRepository.findById(10L)).thenReturn(Optional.of(vacancy));
        when(restTemplate.postForObject(eq("http://ai-service/embeddings/match"), any(HttpEntity.class), eq(Map.class)))
                .thenReturn(aiResponse);

        matchingService.calculateMatch(1L, 10L);

        @SuppressWarnings("rawtypes")
        ArgumentCaptor<HttpEntity> requestCaptor = ArgumentCaptor.forClass(HttpEntity.class);
        verify(restTemplate).postForObject(eq("http://ai-service/embeddings/match"), requestCaptor.capture(), eq(Map.class));

        @SuppressWarnings("unchecked")
        Map<String, String> payload = (Map<String, String>) requestCaptor.getValue().getBody();
        assertEquals("No candidate information available", payload.get("candidate_text"));
        assertEquals("No vacancy information available", payload.get("vacancy_text"));
    }

    @Test
    void shouldThrowWrappedRuntimeExceptionWhenAiServiceReturnsNull() {
        Profile profile = Profile.builder().id(1L).professionalTitle("Dev").build();
        Vacancy vacancy = Vacancy.builder().id(10L).title("Role").build();

        when(profileRepository.findByUserId(1L)).thenReturn(Optional.of(profile));
        when(vacancyRepository.findById(10L)).thenReturn(Optional.of(vacancy));
        when(restTemplate.postForObject(eq("http://ai-service/embeddings/match"), any(HttpEntity.class), eq(Map.class)))
                .thenReturn(null);

        RuntimeException exception = assertThrows(RuntimeException.class,
                () -> matchingService.calculateMatch(1L, 10L));

        assertTrue(exception.getMessage().contains("Error calling AI service"));
        assertTrue(exception.getCause().getMessage().contains("AI service returned null response"));
    }

    @Test
    void shouldThrowWrappedRuntimeExceptionWhenAiServiceCallFails() {
        Profile profile = Profile.builder().id(1L).professionalTitle("Dev").build();
        Vacancy vacancy = Vacancy.builder().id(10L).title("Role").build();

        when(profileRepository.findByUserId(1L)).thenReturn(Optional.of(profile));
        when(vacancyRepository.findById(10L)).thenReturn(Optional.of(vacancy));
        when(restTemplate.postForObject(eq("http://ai-service/embeddings/match"), any(HttpEntity.class), eq(Map.class)))
                .thenThrow(new RuntimeException("service unavailable"));

        RuntimeException exception = assertThrows(RuntimeException.class,
                () -> matchingService.calculateMatch(1L, 10L));

        assertTrue(exception.getMessage().contains("Failed to calculate matching"));
        assertTrue(exception.getMessage().contains("service unavailable"));
    }

        @Test
        void shouldSkipVacancySectionsWhenCollectionsAreEmptyAndSalaryRangeIsIncomplete() {
                Profile profile = Profile.builder().id(1L).professionalTitle("Dev").build();
                Vacancy vacancy = Vacancy.builder()
                                .id(10L)
                                .title("Role")
                                .description("Desc")
                                .technologies(List.of())
                                .softSkills(List.of())
                                .technicalRequirements(List.of())
                                .location("Bogota")
                                .minSalary(5000.0)
                                .maxSalary(null)
                                .build();

                Map<String, Object> aiResponse = Map.of(
                                "similarity_score", 0.5,
                                "compatibility_percentage", 50.0f,
                                "compatibility_level", "medium",
                                "feedback", "Neutral",
                                "used_llm_feedback", false);

                when(profileRepository.findByUserId(1L)).thenReturn(Optional.of(profile));
                when(vacancyRepository.findById(10L)).thenReturn(Optional.of(vacancy));
                when(restTemplate.postForObject(eq("http://ai-service/embeddings/match"), any(HttpEntity.class), eq(Map.class)))
                                .thenReturn(aiResponse);

                matchingService.calculateMatch(1L, 10L);

                @SuppressWarnings("rawtypes")
                ArgumentCaptor<HttpEntity> requestCaptor = ArgumentCaptor.forClass(HttpEntity.class);
                verify(restTemplate).postForObject(eq("http://ai-service/embeddings/match"), requestCaptor.capture(), eq(Map.class));

                @SuppressWarnings("unchecked")
                Map<String, String> payload = (Map<String, String>) requestCaptor.getValue().getBody();
                String vacancyText = payload.get("vacancy_text");

                assertTrue(vacancyText.contains("Title: Role"));
                assertTrue(vacancyText.contains("Description: Desc"));
                assertTrue(vacancyText.contains("Location: Bogota"));
                assertTrue(!vacancyText.contains("Technologies:"));
                assertTrue(!vacancyText.contains("Soft Skills:"));
                assertTrue(!vacancyText.contains("Technical Requirements:"));
                assertTrue(!vacancyText.contains("Salary Range:"));
        }
}

