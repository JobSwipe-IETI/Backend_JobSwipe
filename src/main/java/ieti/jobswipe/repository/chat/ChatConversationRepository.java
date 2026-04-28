package ieti.jobswipe.repository.chat;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import ieti.jobswipe.model.entity.ChatConversation;
import ieti.jobswipe.repository.projection.ConversationSummaryProjection;

public interface ChatConversationRepository extends JpaRepository<ChatConversation, Long> {

    Optional<ChatConversation> findByVacancyIdAndCompanyIdAndCandidateId(Long vacancyId, Long companyId, Long candidateId);

    @Query("""
            select c
            from ChatConversation c
            where c.id = :conversationId
              and (c.companyId = :userId or c.candidateId = :userId)
            """)
    Optional<ChatConversation> findAccessibleConversation(
            @Param("conversationId") Long conversationId,
            @Param("userId") Long userId);

    @Query(value = """
            SELECT
                c.id AS conversationId,
                c.vacancy_id AS vacancyId,
                v.title AS vacancyTitle,
                c.candidate_id AS counterpartId,
                u.name AS counterpartName,
                u.role AS counterpartRole,
                c.initiated_by_user_id AS initiatedByUserId,
                c.last_message_preview AS lastMessagePreview,
                c.last_message_at AS lastMessageAt,
                c.company_unread_count AS unreadCount,
                c.created_at AS createdAt,
                c.updated_at AS updatedAt
            FROM chat_conversations c
            JOIN users u ON u.id = c.candidate_id
            LEFT JOIN vacancies v ON v.id = c.vacancy_id
            WHERE c.company_id = :companyId
            ORDER BY COALESCE(c.last_message_at, c.updated_at) DESC
            LIMIT :limit
            """, nativeQuery = true)
    List<ConversationSummaryProjection> findCompanyConversationSummaries(
            @Param("companyId") Long companyId,
            @Param("limit") int limit);

    @Query(value = """
            SELECT
                c.id AS conversationId,
                c.vacancy_id AS vacancyId,
                v.title AS vacancyTitle,
                c.company_id AS counterpartId,
                u.name AS counterpartName,
                u.role AS counterpartRole,
                c.initiated_by_user_id AS initiatedByUserId,
                c.last_message_preview AS lastMessagePreview,
                c.last_message_at AS lastMessageAt,
                c.candidate_unread_count AS unreadCount,
                c.created_at AS createdAt,
                c.updated_at AS updatedAt
            FROM chat_conversations c
            JOIN users u ON u.id = c.company_id
            LEFT JOIN vacancies v ON v.id = c.vacancy_id
            WHERE c.candidate_id = :candidateId
            ORDER BY COALESCE(c.last_message_at, c.updated_at) DESC
            LIMIT :limit
            """, nativeQuery = true)
    List<ConversationSummaryProjection> findCandidateConversationSummaries(
            @Param("candidateId") Long candidateId,
            @Param("limit") int limit);
}
