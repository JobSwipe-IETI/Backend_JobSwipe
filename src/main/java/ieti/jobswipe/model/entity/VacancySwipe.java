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
        name = "vacancy_swipes",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_vacancy_swipes_user_vacancy", columnNames = { "user_id", "vacancy_id" })
        },
        indexes = {
            @Index(name = "idx_vacancy_swipes_user", columnList = "user_id"),
            @Index(name = "idx_vacancy_swipes_vacancy_decision_updated", columnList = "vacancy_id,decision,updated_at"),
            @Index(name = "idx_vacancy_swipes_user_vacancy_decision", columnList = "user_id,vacancy_id,decision")
        })
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class VacancySwipe {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "vacancy_id", nullable = false)
    private Long vacancyId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private SwipeDecisionType decision;

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
