package ieti.jobswipe.dto;

import java.util.List;

import ieti.jobswipe.model.SwipeDecisionType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CompanyCandidateDecisionRequest {
    private SwipeDecisionType decision;
    private String rejectionReason;
    private List<String> rejectionTags;
    private List<String> missingTechnologies;
    private List<String> missingResponsibilities;
    private List<String> missingTechnicalRequirements;
    private String expectedExperienceLevel;
    private String aiSummary;
    private String rejectionComment;
}
