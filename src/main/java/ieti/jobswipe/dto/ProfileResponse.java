package ieti.jobswipe.dto;

import java.time.LocalDateTime;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record ProfileResponse(
        Long id,
        String professionalTitle,
        String summary,
        String skills,
        String experience,
        String education,
        String location,
        String nationality,
        String phoneNumber,
        Boolean onboardingCompleted,
        LocalDateTime createdAt,
        LocalDateTime updatedAt,
        CandidateProfileResponse candidateProfile,
        CompanyProfileResponse companyProfile) {
}