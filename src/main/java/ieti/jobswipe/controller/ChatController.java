package ieti.jobswipe.controller;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import ieti.jobswipe.dto.ChatMessageRequest;
import ieti.jobswipe.dto.ChatMessageResponse;
import ieti.jobswipe.dto.ConversationSummaryResponse;
import ieti.jobswipe.dto.CreateConversationRequest;
import ieti.jobswipe.service.ChatService;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/chat")
@Validated
public class ChatController {

    private final ChatService chatService;

    public ChatController(ChatService chatService) {
        this.chatService = chatService;
    }

    @GetMapping("/conversations")
    public ResponseEntity<List<ConversationSummaryResponse>> getConversations(
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam(defaultValue = "50") Integer limit) {
        if (limit == null || limit <= 0 || limit > 100) {
            return ResponseEntity.badRequest().build();
        }

        Long userId = Long.parseLong(jwt.getSubject());
        return ResponseEntity.ok(chatService.getConversationsForUser(userId, limit));
    }

    @PostMapping("/conversations")
    public ResponseEntity<ConversationSummaryResponse> createConversation(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody CreateConversationRequest request) {
        Long userId = Long.parseLong(jwt.getSubject());
        try {
            ConversationSummaryResponse response = chatService.startConversation(
                    userId,
                    request.getVacancyId(),
                    request.getCandidateId());
            return ResponseEntity.status(HttpStatus.CREATED).body(response);
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.badRequest().build();
        } catch (RuntimeException ex) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }
    }

    @GetMapping("/conversations/{conversationId}/messages")
    public ResponseEntity<List<ChatMessageResponse>> getMessages(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable Long conversationId,
            @RequestParam(defaultValue = "80") Integer limit) {
        if (limit == null || limit <= 0 || limit > 200) {
            return ResponseEntity.badRequest().build();
        }

        Long userId = Long.parseLong(jwt.getSubject());
        try {
            return ResponseEntity.ok(chatService.getConversationMessages(userId, conversationId, limit));
        } catch (RuntimeException ex) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }
    }

    @PostMapping("/conversations/{conversationId}/messages")
    public ResponseEntity<ChatMessageResponse> sendMessage(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable Long conversationId,
            @Valid @RequestBody ChatMessageRequest request) {
        Long userId = Long.parseLong(jwt.getSubject());
        try {
            return ResponseEntity.status(HttpStatus.CREATED)
                    .body(chatService.sendMessage(
                            userId,
                            conversationId,
                            request.getContent(),
                            request.getClientMessageId()));
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.badRequest().build();
        } catch (RuntimeException ex) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }
    }

    @PostMapping("/conversations/{conversationId}/read")
    public ResponseEntity<Void> markAsRead(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable Long conversationId) {
        Long userId = Long.parseLong(jwt.getSubject());
        try {
            chatService.markConversationAsRead(userId, conversationId);
            return ResponseEntity.noContent().build();
        } catch (RuntimeException ex) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }
    }
}
