package ieti.jobswipe.dto.company;

import java.time.LocalDateTime;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record CompanyProfileResponse(
        Long id,
        String companyName,
        String legalId,
        String industry,
        String companySize,
        String website,
        String headquartersLocation,
        String companyDescription,
        String hiringContactName,
        String hiringContactEmail,
        LocalDateTime createdAt,
        LocalDateTime updatedAt) {
}