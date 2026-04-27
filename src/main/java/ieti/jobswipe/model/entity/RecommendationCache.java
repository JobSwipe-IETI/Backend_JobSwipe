package ieti.jobswipe.model.entity;

import java.time.Instant;
import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(
        name = "recommendation_cache",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_recommendation_cache_user_vacancy", columnNames = { "user_id", "vacancy_id" })
        },
        indexes = {
                @Index(name = "idx_recommendation_cache_updated_at", columnList = "updated_at"),
                @Index(name = "idx_recommendation_cache_user_vacancy_updated", columnList = "user_id,vacancy_id,updated_at")
        })
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RecommendationCache {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "vacancy_id", nullable = false)
    private Long vacancyId;

    @Column(name = "similarity_score")
    private Double similarityScore;

    @Column(name = "compatibility_percentage")
    private Float compatibilityPercentage;

    @Column(name = "compatibility_level", length = 32)
    private String compatibilityLevel;

    @Column(name = "feedback", columnDefinition = "TEXT")
    private String feedback;

    @Column(name = "used_llm_feedback")
    private Boolean usedLlmFeedback;

        @Column(name = "source_profile_updated_at")
        private LocalDateTime sourceProfileUpdatedAt;

        @Column(name = "source_vacancy_updated_at")
        private LocalDateTime sourceVacancyUpdatedAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}
