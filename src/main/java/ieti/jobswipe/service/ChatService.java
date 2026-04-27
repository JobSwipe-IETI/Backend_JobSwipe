package ieti.jobswipe.service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import ieti.jobswipe.dto.ChatMessageResponse;
import ieti.jobswipe.dto.ConversationSummaryResponse;
import ieti.jobswipe.model.ChatConversation;
import ieti.jobswipe.model.ChatMessage;
import ieti.jobswipe.model.Role;
import ieti.jobswipe.model.SwipeDecisionType;
import ieti.jobswipe.model.User;
import ieti.jobswipe.model.Vacancy;
import ieti.jobswipe.repository.ChatConversationRepository;
import ieti.jobswipe.repository.ChatMessageRepository;
import ieti.jobswipe.repository.CompanyCandidateDecisionRepository;
import ieti.jobswipe.repository.ConversationSummaryProjection;
import ieti.jobswipe.repository.UserRepository;
import ieti.jobswipe.repository.VacancyRepository;

@Service
public class ChatService {

    private final ChatConversationRepository chatConversationRepository;
    private final ChatMessageRepository chatMessageRepository;
    private final CompanyCandidateDecisionRepository companyCandidateDecisionRepository;
    private final UserRepository userRepository;
    private final VacancyRepository vacancyRepository;
    private final SupabaseChatRealtimePublisher supabaseChatRealtimePublisher;

    public ChatService(
            ChatConversationRepository chatConversationRepository,
            ChatMessageRepository chatMessageRepository,
            CompanyCandidateDecisionRepository companyCandidateDecisionRepository,
            UserRepository userRepository,
            VacancyRepository vacancyRepository,
            SupabaseChatRealtimePublisher supabaseChatRealtimePublisher) {
        this.chatConversationRepository = chatConversationRepository;
        this.chatMessageRepository = chatMessageRepository;
        this.companyCandidateDecisionRepository = companyCandidateDecisionRepository;
        this.userRepository = userRepository;
        this.vacancyRepository = vacancyRepository;
        this.supabaseChatRealtimePublisher = supabaseChatRealtimePublisher;

    }

    @Transactional(readOnly = true)
    public List<ConversationSummaryResponse> getConversationsForUser(Long userId, int limit) {
        User user = getRequiredUser(userId);
        int effectiveLimit = Math.max(1, Math.min(limit, 100));
        List<ConversationSummaryProjection> rows = user.getRole() == Role.COMPANY
                ? chatConversationRepository.findCompanyConversationSummaries(userId, effectiveLimit)
                : chatConversationRepository.findCandidateConversationSummaries(userId, effectiveLimit);

        return rows.stream()
                .map(this::mapConversationSummary)
                .toList();
    }

    @Transactional
    public ConversationSummaryResponse startConversation(Long companyId, Long vacancyId, Long candidateId) {
        User company = getRequiredUser(companyId);
        if (company.getRole() != Role.COMPANY) {
            throw new IllegalArgumentException("Only companies can start conversations.");
        }

        User candidate = getRequiredUser(candidateId);
        if (candidate.getRole() != Role.CANDIDATE) {
            throw new IllegalArgumentException("Conversation counterpart must be a candidate.");
        }

        Vacancy vacancy = vacancyRepository.findById(vacancyId)
                .orElseThrow(() -> new RuntimeException("Vacancy not found."));
        if (!vacancy.getCompany().getId().equals(companyId)) {
            throw new IllegalArgumentException("Conversation can only be started from company-owned vacancies.");
        }

        boolean hasMatch = companyCandidateDecisionRepository.existsByCompanyIdAndCandidateIdAndVacancyIdAndDecision(
            companyId,
            candidateId,
            vacancyId,
            SwipeDecisionType.LIKE
        );
        if (!hasMatch) {
            throw new IllegalArgumentException("Conversation requires an active match.");
        }

        Optional<ChatConversation> existingConversation = chatConversationRepository
                .findByVacancyIdAndCompanyIdAndCandidateId(vacancyId, companyId, candidateId);

        ChatConversation conversation = existingConversation
                .orElseGet(() -> chatConversationRepository.save(ChatConversation.builder()
                        .vacancyId(vacancyId)
                        .companyId(companyId)
                        .candidateId(candidateId)
                        .initiatedByUserId(companyId)
                        .candidateUnreadCount(0)
                        .companyUnreadCount(0)
                        .build()));

        // Notificación en tiempo real eliminada: ahora se maneja por Firestore.

        return buildSummaryForConversation(conversation, company);
    }

    @Transactional(readOnly = true)
    public List<ChatMessageResponse> getConversationMessages(Long userId, Long conversationId, int limit) {
        User currentUser = getRequiredUser(userId);
        ChatConversation conversation = getAccessibleConversation(conversationId, userId);

        List<ChatMessage> rows = chatMessageRepository.findByConversationIdOrderByCreatedAtDesc(
                conversation.getId(),
                PageRequest.of(0, Math.max(1, Math.min(limit, 200))));

        List<ChatMessageResponse> messages = new ArrayList<>(rows.size());
        for (ChatMessage row : rows) {
            messages.add(mapChatMessage(row, currentUser.getId()));
        }
        Collections.reverse(messages);
        return messages;
    }

    @Transactional
    public ChatMessageResponse sendMessage(
            Long userId,
            Long conversationId,
            String content,
            String clientMessageId) {

        User currentUser = getRequiredUser(userId);
        ChatConversation conversation = getAccessibleConversation(conversationId, userId);

        String normalizedContent = normalizeMessageContent(content);
        if (!StringUtils.hasText(normalizedContent)) {
            throw new IllegalArgumentException("Message content is required.");
        }

        ChatMessage message = chatMessageRepository.save(ChatMessage.builder()
                .conversationId(conversationId)
                .senderId(userId)
                .senderRole(currentUser.getRole())
                .content(normalizedContent)
                .build());

        if (currentUser.getRole() == Role.COMPANY) {
            conversation.setCandidateUnreadCount(conversation.getCandidateUnreadCount() + 1);
        } else {
            conversation.setCompanyUnreadCount(conversation.getCompanyUnreadCount() + 1);
        }

        conversation.setLastMessagePreview(buildPreview(normalizedContent));
        conversation.setLastMessageAt(message.getCreatedAt());
        chatConversationRepository.save(conversation);

        ChatMessageResponse response = mapChatMessage(message, userId, clientMessageId);

        supabaseChatRealtimePublisher.publishMessage(response);

        return response;
    }

    @Transactional
    public void markConversationAsRead(Long userId, Long conversationId) {
        User currentUser = getRequiredUser(userId);
        ChatConversation conversation = getAccessibleConversation(conversationId, userId);

        if (currentUser.getRole() == Role.COMPANY) {
            if (conversation.getCompanyUnreadCount() != 0) {
                conversation.setCompanyUnreadCount(0);
                chatConversationRepository.save(conversation);
            }
            return;
        }

        if (conversation.getCandidateUnreadCount() != 0) {
            conversation.setCandidateUnreadCount(0);
            chatConversationRepository.save(conversation);
        }
    }

    @Transactional(readOnly = true)
    public ConversationSummaryResponse getConversationSummaryForUser(Long userId, Long conversationId) {
        User currentUser = getRequiredUser(userId);
        ChatConversation conversation = getAccessibleConversation(conversationId, userId);
        return buildSummaryForConversation(conversation, currentUser);
    }

    private ConversationSummaryResponse buildSummaryForConversation(ChatConversation conversation, User currentUser) {
        User counterpart = currentUser.getRole() == Role.COMPANY
                ? getRequiredUser(conversation.getCandidateId())
                : getRequiredUser(conversation.getCompanyId());
        Vacancy vacancy = vacancyRepository.findById(conversation.getVacancyId())
                .orElse(null);

        int unreadCount = currentUser.getRole() == Role.COMPANY
                ? conversation.getCompanyUnreadCount()
                : conversation.getCandidateUnreadCount();

        return ConversationSummaryResponse.builder()
                .conversationId(conversation.getId())
                .vacancyId(conversation.getVacancyId())
                .vacancyTitle(vacancy != null && StringUtils.hasText(vacancy.getTitle()) ? vacancy.getTitle() : "Vacante")
                .counterpartId(counterpart.getId())
                .counterpartName(StringUtils.hasText(counterpart.getName()) ? counterpart.getName() : "Usuario")
                .counterpartRole(counterpart.getRole().name())
                .initiatedByUserId(conversation.getInitiatedByUserId())
                .lastMessagePreview(conversation.getLastMessagePreview())
                .lastMessageAt(conversation.getLastMessageAt())
                .unreadCount(unreadCount)
                .createdAt(conversation.getCreatedAt())
                .updatedAt(conversation.getUpdatedAt())
                .build();
    }

    private ConversationSummaryResponse mapConversationSummary(ConversationSummaryProjection row) {
        Integer unreadCount = row.getUnreadCount();
        int safeUnreadCount = unreadCount == null ? 0 : unreadCount.intValue();
        return ConversationSummaryResponse.builder()
                .conversationId(row.getConversationId())
                .vacancyId(row.getVacancyId())
                .vacancyTitle(StringUtils.hasText(row.getVacancyTitle()) ? row.getVacancyTitle() : "Vacante")
                .counterpartId(row.getCounterpartId())
                .counterpartName(StringUtils.hasText(row.getCounterpartName()) ? row.getCounterpartName() : "Usuario")
                .counterpartRole(row.getCounterpartRole())
                .initiatedByUserId(row.getInitiatedByUserId())
                .lastMessagePreview(row.getLastMessagePreview())
                .lastMessageAt(row.getLastMessageAt())
            .unreadCount(safeUnreadCount)
                .createdAt(row.getCreatedAt())
                .updatedAt(row.getUpdatedAt())
                .build();
    }
        // Método buildSummaryLight eliminado: ya no es necesario para lógica de WebSocket/chat.
    private ChatMessageResponse mapChatMessage(ChatMessage message, Long currentUserId) {
        return mapChatMessage(message, currentUserId, null);
    }

    private ChatMessageResponse mapChatMessage(
            ChatMessage message,
            Long currentUserId,
            String clientMessageId) {
        return ChatMessageResponse.builder()
                .id(message.getId())
                .conversationId(message.getConversationId())
                .senderId(message.getSenderId())
                .senderRole(message.getSenderRole().name())
                .content(message.getContent())
                .createdAt(message.getCreatedAt())
                .mine(message.getSenderId().equals(currentUserId))
                .clientMessageId(clientMessageId)
                .build();
    }

    private User getRequiredUser(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found."));
    }

    private ChatConversation getAccessibleConversation(Long conversationId, Long userId) {
        return chatConversationRepository.findAccessibleConversation(conversationId, userId)
                .orElseThrow(() -> new RuntimeException("Conversation not found."));
    }

    private String normalizeMessageContent(String content) {
        if (!StringUtils.hasText(content)) {
            return null;
        }
        String normalized = content.trim().replaceAll("\\s+", " ");
        if (normalized.length() > 2000) {
            return normalized.substring(0, 2000);
        }
        return normalized;
    }

    private String buildPreview(String content) {
        if (!StringUtils.hasText(content)) {
            return null;
        }
        if (content.length() <= 180) {
            return content;
        }
        return content.substring(0, 177) + "...";
    }
}
