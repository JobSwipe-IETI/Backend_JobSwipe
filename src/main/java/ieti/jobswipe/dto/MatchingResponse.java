package ieti.jobswipe.dto;

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
public class MatchingResponse {
    private Double similarityScore;
    private Float compatibilityPercentage;
    private String compatibilityLevel;
    private String feedback;
    private Boolean usedLlmFeedback;
}

