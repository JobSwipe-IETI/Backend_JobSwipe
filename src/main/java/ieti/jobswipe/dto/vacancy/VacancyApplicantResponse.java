package ieti.jobswipe.dto.vacancy;

import java.time.LocalDateTime;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class VacancyApplicantResponse {
    private Long candidateId;
    private String candidateName;
    private Float compatibilityPercentage;
    private String compatibilityLevel;
    private String feedback;
    private LocalDateTime appliedAt;
}
