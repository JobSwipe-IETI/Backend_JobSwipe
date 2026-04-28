package ieti.jobswipe.dto.profile;

import java.time.LocalDateTime;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record CandidateProfileResponse(
        Long id,
        String languages,
        Double expectedSalary,
        String availability,
        String sector,
        String portfolioUrl,
        String githubUrl,
        String linkedinUrl,
        String cvUrl,
        LocalDateTime createdAt,
        LocalDateTime updatedAt) {
}