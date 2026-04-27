package ieti.jobswipe.model.entity;

import java.time.LocalDateTime;

import ieti.jobswipe.model.SwipeDecisionType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(
        name = "company_candidate_decisions",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_company_candidate_vacancy", columnNames = {
                        "company_id", "candidate_id", "vacancy_id"
                })
        },
        indexes = {
                @Index(name = "idx_company_candidate_decisions_candidate", columnList = "candidate_id"),
                @Index(name = "idx_company_candidate_decisions_company", columnList = "company_id"),
                @Index(name = "idx_company_candidate_decisions_candidate_decision_updated", columnList = "candidate_id,decision,updated_at"),
                @Index(name = "idx_company_candidate_decisions_company_decision_updated", columnList = "company_id,decision,updated_at")
        })
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CompanyCandidateDecision {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "company_id", nullable = false)
    private Long companyId;

    @Column(name = "candidate_id", nullable = false)
    private Long candidateId;

    @Column(name = "vacancy_id", nullable = false)
    private Long vacancyId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private SwipeDecisionType decision;

        @Column(name = "rejection_reason", length = 120)
        private String rejectionReason;

        @Column(name = "rejection_tags", length = 600)
        private String rejectionTags;

        @Column(name = "missing_technologies", length = 600)
        private String missingTechnologies;

        @Column(name = "missing_responsibilities", length = 1200)
        private String missingResponsibilities;

        @Column(name = "missing_technical_requirements", length = 1200)
        private String missingTechnicalRequirements;

        @Column(name = "expected_experience_level", length = 32)
        private String expectedExperienceLevel;

        @Column(name = "ai_summary", columnDefinition = "TEXT")
        private String aiSummary;

        @Column(name = "rejection_comment", length = 1200)
        private String rejectionComment;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    private void onCreate() {
        LocalDateTime now = LocalDateTime.now();
        this.createdAt = now;
        this.updatedAt = now;
    }

    @PreUpdate
    private void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }
}