package ieti.jobswipe.service.profile;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.DefaultUriBuilderFactory;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import ieti.jobswipe.model.entity.Profile;
import ieti.jobswipe.model.entity.ProfileFeedback;
import ieti.jobswipe.repository.profile.ProfileFeedbackRepository;
import ieti.jobswipe.repository.profile.ProfileRepository;

@Service
public class ProfileFeedbackService {

    private static final Logger logger = LoggerFactory.getLogger(ProfileFeedbackService.class);

    private final ProfileFeedbackRepository feedbackRepository;
    private final ProfileRepository profileRepository;
    private final RestTemplate restTemplate;
    private final TransactionTemplate transactionTemplate;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final String aiServiceBaseUrl;

    public ProfileFeedbackService(ProfileFeedbackRepository feedbackRepository,
            ProfileRepository profileRepository,
            PlatformTransactionManager transactionManager,
            @Value("${app.ai-service.base-url:http://localhost:8001}") String aiServiceBaseUrl) {
        this.feedbackRepository = feedbackRepository;
        this.profileRepository = profileRepository;
        this.aiServiceBaseUrl = aiServiceBaseUrl;
        this.restTemplate = new RestTemplate();
        this.restTemplate.setUriTemplateHandler(new DefaultUriBuilderFactory(this.aiServiceBaseUrl));
        this.transactionTemplate = new TransactionTemplate(transactionManager);
    }

    public void analyzeAndSaveAsync(Long profileId) {
        logger.info("🔄 Dispatching async profile analysis for profileId={}", profileId);
        CompletableFuture.runAsync(() -> analyzeAndSave(profileId));
    }

    public void analyzeAndSave(Long profileId) {
        long startNanos = System.nanoTime();
        try {
            logger.info("▶️ Starting profile analysis task profileId={} thread={}",
                    profileId, Thread.currentThread().getName());

                Profile profile = transactionTemplate.execute(status ->
                    profileRepository.findByIdForAnalysis(profileId)
                        .orElseThrow(() -> new IllegalStateException(
                            "Profile not found for analysis profileId=" + profileId)));

            logger.info("🔎 Loaded profile for analysis profileId={} hasUser={} hasCandidateProfile={}",
                    profileId, profile.getUser() != null, profile.getCandidateProfile() != null);

            Map<String, Object> payload = buildPayload(profile);
            String url = this.aiServiceBaseUrl.replaceAll("/$", "") + "/profiles/analyze";
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            String body = objectMapper.writeValueAsString(payload);

            HttpEntity<String> request = new HttpEntity<>(body, headers);

            logger.info("🔄 Calling AI service to analyze profileId={} url={} payloadBytes={}",
                    profileId, url, body.length());
            
            // Use URL string directly, not URI object
            ResponseEntity<String> resp = restTemplate.postForEntity(url, request, String.class);
            long aiCallMs = (System.nanoTime() - startNanos) / 1_000_000;
            logger.info("⏱️ AI service response for profileId={} status={} durationMs={}",
                    profileId, resp.getStatusCode().value(), aiCallMs);

            if (resp.getStatusCode().is2xxSuccessful() && resp.getBody() != null) {
                Profile savedProfile = transactionTemplate.execute(status ->
                    profileRepository.findById(profileId)
                        .orElseThrow(() -> new IllegalStateException(
                            "Profile not found when saving feedback for profileId=" + profileId)));

                transactionTemplate.executeWithoutResult(status -> {
                    ProfileFeedback feedback = feedbackRepository.findByProfile(savedProfile)
                        .orElse(ProfileFeedback.builder().profile(savedProfile).build());
                    feedback.setPayload(resp.getBody());
                    feedbackRepository.save(feedback);
                });
                logger.info("✅ Profile {} analysis completed and saved in {} ms", profileId,
                        (System.nanoTime() - startNanos) / 1_000_000);
            } else {
                logger.warn("AI analyze returned non-2xx: {}", resp.getStatusCode().value());
            }
        } catch (JsonProcessingException | RestClientException e) {
            logger.error("Failed to analyze profile {} after {} ms: {}", profileId,
                    (System.nanoTime() - startNanos) / 1_000_000, e.getMessage(), e);
        } catch (RuntimeException e) {
            logger.error("Unexpected failure analyzing profile {} after {} ms: {}", profileId,
                    (System.nanoTime() - startNanos) / 1_000_000, e.getMessage(), e);
        }
    }

    private Map<String, Object> buildPayload(Profile profile) {
        Map<String, Object> m = new HashMap<>();
        m.put("profileId", profile.getId());
        m.put("displayName", profile.getUser() != null ? profile.getUser().getName() : null);
        m.put("professionalTitle", profile.getProfessionalTitle());
        m.put("summary", profile.getSummary());
        m.put("skills", profile.getSkills());
        m.put("experience", profile.getExperience());
        m.put("education", profile.getEducation());
        m.put("location", profile.getLocation());
        m.put("nationality", profile.getNationality());
        m.put("languages", profile.getCandidateProfile() != null ? profile.getCandidateProfile().getLanguages() : null);
        m.put("expectedSalary", profile.getCandidateProfile() != null ? profile.getCandidateProfile().getExpectedSalary() : null);
        m.put("availability", profile.getCandidateProfile() != null ? profile.getCandidateProfile().getAvailability() : null);
        m.put("email", profile.getUser() != null ? profile.getUser().getEmail() : null);
        m.put("phoneNumber", profile.getPhoneNumber());
        m.put("github", profile.getCandidateProfile() != null ? profile.getCandidateProfile().getGithubUrl() : null);
        m.put("linkedin", profile.getCandidateProfile() != null ? profile.getCandidateProfile().getLinkedinUrl() : null);
        return m;
    }
}
