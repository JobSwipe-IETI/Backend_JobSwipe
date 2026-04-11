package ieti.JobSwipe.service;

import java.util.HashMap;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;

import com.fasterxml.jackson.databind.ObjectMapper;

import ieti.JobSwipe.dto.MatchingResponse;
import ieti.JobSwipe.exception.ErrorMessages;
import ieti.JobSwipe.exception.ProfileNotFoundException;
import ieti.JobSwipe.exception.VacancyNotFoundException;
import ieti.JobSwipe.model.Profile;
import ieti.JobSwipe.model.Vacancy;
import ieti.JobSwipe.repository.ProfileRepository;
import ieti.JobSwipe.repository.VacancyRepository;

@Service
public class MatchingService {

    private static final Logger logger = LoggerFactory.getLogger(MatchingService.class);
    private final ProfileRepository profileRepository;
    private final VacancyRepository vacancyRepository;
    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Value("${app.ai-service.base-url}")
    private String aiServiceBaseUrl;

    public MatchingService(ProfileRepository profileRepository,
            VacancyRepository vacancyRepository,
            RestTemplate restTemplate) {
        this.profileRepository = profileRepository;
        this.vacancyRepository = vacancyRepository;
        this.restTemplate = restTemplate;
    }

    @Transactional(readOnly = true)
    public MatchingResponse calculateMatch(Long userId, Long vacancyId) {
        logger.info("🔍 MatchingService.calculateMatch called with userId={}, vacancyId={}", userId, vacancyId);

        // Get candidate profile
        Profile candidateProfile = profileRepository.findByUserId(userId)
                .orElseThrow(() -> new ProfileNotFoundException(ErrorMessages.PROFILE_NOT_FOUND));
        logger.info("✅ Candidate profile found: id={}", candidateProfile.getId());

        // Get vacancy
        Vacancy vacancy = vacancyRepository.findById(vacancyId)
                .orElseThrow(() -> new VacancyNotFoundException(ErrorMessages.VACANCY_NOT_FOUND));
        logger.info("✅ Vacancy found: id={}", vacancy.getId());

        // Build candidate text from profile
        String candidateText = buildCandidateText(candidateProfile);
        logger.info("📝 Candidate text length: {}", candidateText.length());

        // Build vacancy text from vacancy
        String vacancyText = buildVacancyText(vacancy);
        logger.info("📝 Vacancy text length: {}", vacancyText.length());

        // Call AI service
        MatchingResponse response = callAiService(candidateText, vacancyText);
        logger.info("✅ AI service response: compatibility={}%, level={}", 
            response.getCompatibilityPercentage(), 
            response.getCompatibilityLevel());

        return response;
    }

    private String buildCandidateText(Profile profile) {
        StringBuilder sb = new StringBuilder();
        if (profile.getProfessionalTitle() != null) {
            sb.append("Professional Title: ").append(profile.getProfessionalTitle()).append("\n");
        }
        if (profile.getSummary() != null) {
            sb.append("Summary: ").append(profile.getSummary()).append("\n");
        }
        if (profile.getSkills() != null) {
            sb.append("Skills: ").append(profile.getSkills()).append("\n");
        }
        if (profile.getExperience() != null) {
            sb.append("Experience: ").append(profile.getExperience()).append("\n");
        }
        if (profile.getEducation() != null) {
            sb.append("Education: ").append(profile.getEducation()).append("\n");
        }
        if (profile.getLocation() != null) {
            sb.append("Location: ").append(profile.getLocation()).append("\n");
        }
        if (profile.getCandidateProfile() != null) {
            if (profile.getCandidateProfile().getSector() != null) {
                sb.append("Sector: ").append(profile.getCandidateProfile().getSector()).append("\n");
            }
            if (profile.getCandidateProfile().getExpectedSalary() != null) {
                sb.append("Expected Salary: ").append(profile.getCandidateProfile().getExpectedSalary()).append("\n");
            }
            if (profile.getCandidateProfile().getAvailability() != null) {
                sb.append("Availability: ").append(profile.getCandidateProfile().getAvailability()).append("\n");
            }
            if (profile.getCandidateProfile().getLanguages() != null) {
                sb.append("Languages: ").append(profile.getCandidateProfile().getLanguages()).append("\n");
            }
        }
        return sb.toString().isEmpty() ? "No candidate information available" : sb.toString();
    }

    private String buildVacancyText(Vacancy vacancy) {
        StringBuilder sb = new StringBuilder();
        if (vacancy.getTitle() != null) {
            sb.append("Title: ").append(vacancy.getTitle()).append("\n");
        }
        if (vacancy.getDescription() != null) {
            sb.append("Description: ").append(vacancy.getDescription()).append("\n");
        }
        if (vacancy.getTechnologies() != null && !vacancy.getTechnologies().isEmpty()) {
            sb.append("Technologies: ").append(String.join(", ", vacancy.getTechnologies())).append("\n");
        }
        if (vacancy.getSoftSkills() != null && !vacancy.getSoftSkills().isEmpty()) {
            sb.append("Soft Skills: ").append(String.join(", ", vacancy.getSoftSkills())).append("\n");
        }
        if (vacancy.getTechnicalRequirements() != null && !vacancy.getTechnicalRequirements().isEmpty()) {
            sb.append("Technical Requirements: ").append(String.join(", ", vacancy.getTechnicalRequirements())).append("\n");
        }
        if (vacancy.getLocation() != null) {
            sb.append("Location: ").append(vacancy.getLocation()).append("\n");
        }
        if (vacancy.getMinSalary() != null && vacancy.getMaxSalary() != null) {
            sb.append("Salary Range: ").append(vacancy.getMinSalary()).append(" - ").append(vacancy.getMaxSalary()).append("\n");
        }
        if (vacancy.getExperienceLevel() != null) {
            sb.append("Experience Level: ").append(vacancy.getExperienceLevel()).append("\n");
        }
        if (vacancy.getSector() != null) {
            sb.append("Sector: ").append(vacancy.getSector()).append("\n");
        }
        return sb.toString().isEmpty() ? "No vacancy information available" : sb.toString();
    }

    private MatchingResponse callAiService(String candidateText, String vacancyText) {
        try {
            String url = aiServiceBaseUrl + "/embeddings/match";
            logger.info("📡 Calling AI service: {}", url);

            // Build request payload
            Map<String, String> payload = new HashMap<>();
            payload.put("candidate_text", candidateText);
            payload.put("vacancy_text", vacancyText);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            HttpEntity<Map<String, String>> request = new HttpEntity<>(payload, headers);

            // Call AI service
            @SuppressWarnings("unchecked")
            Map<String, Object> aiResponse = restTemplate.postForObject(url, request, Map.class);

            if (aiResponse == null) {
                logger.error("❌ AI service returned null response");
                throw new RuntimeException("AI service returned null response");
            }

            logger.info("✅ AI service response: {}", aiResponse);

            // Parse response
            MatchingResponse response = MatchingResponse.builder()
                    .similarityScore(((Number) aiResponse.get("similarity_score")).doubleValue())
                    .compatibilityPercentage(((Number) aiResponse.get("compatibility_percentage")).floatValue())
                    .compatibilityLevel((String) aiResponse.get("compatibility_level"))
                    .feedback((String) aiResponse.get("feedback"))
                    .usedLlmFeedback((Boolean) aiResponse.get("used_llm_feedback"))
                    .build();

            return response;
        } catch (Exception e) {
            logger.error("❌ Error calling AI service: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to calculate matching: " + e.getMessage(), e);
        }
    }
}
