package ieti.jobswipe.service.matching;

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

import ieti.jobswipe.dto.matching.MatchingResponse;
import ieti.jobswipe.exception.ErrorMessages;
import ieti.jobswipe.exception.ProfileNotFoundException;
import ieti.jobswipe.exception.VacancyNotFoundException;
import ieti.jobswipe.model.entity.Profile;
import ieti.jobswipe.model.entity.Vacancy;
import ieti.jobswipe.repository.profile.ProfileRepository;
import ieti.jobswipe.repository.vacancy.VacancyRepository;

@Service
public class MatchingService {

    private static final Logger logger = LoggerFactory.getLogger(MatchingService.class);
    private final ProfileRepository profileRepository;
    private final VacancyRepository vacancyRepository;
    private final RestTemplate restTemplate;

    @Value("${app.ai-service.base-url}")
    private String aiServiceBaseUrl;

    public MatchingService(ProfileRepository profileRepository,
            VacancyRepository vacancyRepository,
            RestTemplate restTemplate) {
        this.profileRepository = profileRepository;
        this.vacancyRepository = vacancyRepository;
        this.restTemplate = restTemplate;
    }

    @Transactional(readOnly = true, noRollbackFor = RuntimeException.class)
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
        appendIfPresent(sb, "Professional Title", profile.getProfessionalTitle());
        appendIfPresent(sb, "Summary", profile.getSummary());
        appendIfPresent(sb, "Skills", profile.getSkills());
        appendIfPresent(sb, "Experience", profile.getExperience());
        appendIfPresent(sb, "Education", profile.getEducation());
        appendIfPresent(sb, "Location", profile.getLocation());
        appendCandidateDetails(sb, profile);

        String candidateStr = sb.toString();
        return candidateStr.isEmpty() ? "No candidate information available" : candidateStr;
    }

    private void appendCandidateDetails(StringBuilder sb, Profile profile) {
        if (profile.getCandidateProfile() == null) {
            return;
        }

        appendIfPresent(sb, "Sector", profile.getCandidateProfile().getSector());
        appendIfPresent(sb, "Expected Salary", profile.getCandidateProfile().getExpectedSalary());
        appendIfPresent(sb, "Availability", profile.getCandidateProfile().getAvailability());
        appendIfPresent(sb, "Languages", profile.getCandidateProfile().getLanguages());
    }

    private void appendIfPresent(StringBuilder sb, String label, Object value) {
        if (value != null) {
            sb.append(label).append(": ").append(value).append("\n");
        }
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
        String vacancyStr = sb.toString();
        return vacancyStr.isEmpty() ? "No vacancy information available" : vacancyStr;
    }

    private MatchingResponse callAiService(String candidateText, String vacancyText) {
        try {
            String url = aiServiceBaseUrl + "/embeddings/match";
            logger.info("\ud83d\udce1 Calling AI service: {}", url);

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
                logger.error("\u274c AI service returned null response");
                throw new IllegalStateException("AI service returned null response");
            }

            logger.info("\u2705 AI service response: {}", aiResponse);

            // Parse response and return directly
            return MatchingResponse.builder()
                    .similarityScore(((Number) aiResponse.get("similarity_score")).doubleValue())
                    .compatibilityPercentage(((Number) aiResponse.get("compatibility_percentage")).floatValue())
                    .compatibilityLevel((String) aiResponse.get("compatibility_level"))
                    .feedback((String) aiResponse.get("feedback"))
                    .usedLlmFeedback((Boolean) aiResponse.get("used_llm_feedback"))
                    .build();
        } catch (IllegalStateException e) {
            String contextMsg = "Error calling AI service: " + e.getMessage();
            throw new IllegalStateException(contextMsg, e);
        } catch (Exception e) {
            logger.error("\u274c Error calling AI service: {}", e.getMessage(), e);
            throw new IllegalStateException("Failed to calculate matching: " + e.getMessage(), e);
        }
    }
}

