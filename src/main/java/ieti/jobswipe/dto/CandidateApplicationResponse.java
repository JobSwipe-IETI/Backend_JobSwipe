package ieti.jobswipe.dto;

import java.time.LocalDateTime;
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
public class CandidateApplicationResponse {
    private Long vacancyId;
    private String vacancyTitle;
    private Long companyId;
    private String companyName;
    private LocalDateTime appliedAt;
    private SwipeDecisionType decision;
    private LocalDateTime decisionAt;
    private boolean matched;
    private String rejectionReason;
    private List<String> rejectionTags;
    private List<String> missingTechnologies;
    private List<String> missingResponsibilities;
    private List<String> missingTechnicalRequirements;
    private String expectedExperienceLevel;
    private String aiSummary;
    private String rejectionComment;
}
