package ieti.JobSwipe.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class VacancyRecommendationResponse {
    private Long vacancyId;
    private String title;
    private String location;
    private Float compatibilityPercentage;
    private String compatibilityLevel;
    private Double similarityScore;
    private String feedback;
}