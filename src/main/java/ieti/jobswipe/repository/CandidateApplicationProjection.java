package ieti.jobswipe.repository;

import java.time.LocalDateTime;

public interface CandidateApplicationProjection {
    Long getVacancyId();

    String getVacancyTitle();

    Long getCompanyId();

    String getCompanyName();

    LocalDateTime getAppliedAt();

    String getDecision();

    LocalDateTime getDecisionAt();

    Boolean getMatched();

    String getRejectionReason();

    String getRejectionTags();

    String getMissingTechnologies();

    String getMissingResponsibilities();

    String getMissingTechnicalRequirements();

    String getExpectedExperienceLevel();

    String getAiSummary();

    String getRejectionComment();
}
