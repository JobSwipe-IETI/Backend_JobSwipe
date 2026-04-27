package ieti.jobswipe.dto.chat;

import java.time.LocalDateTime;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ConversationSummaryResponse {

    private Long conversationId;
    private Long vacancyId;
    private String vacancyTitle;
    private Long counterpartId;
    private String counterpartName;
    private String counterpartRole;
    private Long initiatedByUserId;
    private String lastMessagePreview;
    private LocalDateTime lastMessageAt;
    private Integer unreadCount;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
