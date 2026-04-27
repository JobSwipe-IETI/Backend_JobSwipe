package ieti.jobswipe.repository;

import java.time.LocalDateTime;

public interface ConversationSummaryProjection {
    Long getConversationId();

    Long getVacancyId();

    String getVacancyTitle();

    Long getCounterpartId();

    String getCounterpartName();

    String getCounterpartRole();

    Long getInitiatedByUserId();

    String getLastMessagePreview();

    LocalDateTime getLastMessageAt();

    Integer getUnreadCount();

    LocalDateTime getCreatedAt();

    LocalDateTime getUpdatedAt();
}
