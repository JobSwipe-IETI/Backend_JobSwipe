package ieti.jobswipe.controller.chat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.oauth2.jwt.Jwt;

import ieti.jobswipe.dto.chat.ChatMessageRequest;
import ieti.jobswipe.dto.chat.ChatMessageResponse;
import ieti.jobswipe.dto.chat.ConversationSummaryResponse;
import ieti.jobswipe.dto.chat.CreateConversationRequest;
import ieti.jobswipe.service.chat.ChatService;

@ExtendWith(MockitoExtension.class)
class ChatControllerTest {

    @Mock
    private ChatService chatService;

    private ChatController chatController;
    private Jwt jwt;

    @BeforeEach
    void setUp() {
        chatController = new ChatController(chatService);
        jwt = Jwt.withTokenValue("token")
                .subject("10")
                .header("alg", "none")
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(3600))
                .build();
    }

    @Test
    void shouldReturnBadRequestForInvalidConversationLimit() {
        ResponseEntity<List<ConversationSummaryResponse>> response = chatController.getConversations(jwt, 0);
        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
    }

    @Test
    void shouldReturnBadRequestForNullConversationLimit() {
        ResponseEntity<List<ConversationSummaryResponse>> response = chatController.getConversations(jwt, null);
        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
    }

    @Test
    void shouldGetConversations() {
        ConversationSummaryResponse summary = ConversationSummaryResponse.builder().conversationId(1L).build();
        when(chatService.getConversationsForUser(10L, 50)).thenReturn(List.of(summary));

        ResponseEntity<List<ConversationSummaryResponse>> response = chatController.getConversations(jwt, 50);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(1, response.getBody().size());
    }

    @Test
    void shouldReturnBadRequestForTooLargeConversationLimit() {
        ResponseEntity<List<ConversationSummaryResponse>> response = chatController.getConversations(jwt, 101);
        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
    }

    @Test
    void shouldCreateConversation() {
        CreateConversationRequest request = CreateConversationRequest.builder().vacancyId(1L).candidateId(20L).build();
        when(chatService.startConversation(10L, 1L, 20L))
                .thenReturn(ConversationSummaryResponse.builder().conversationId(9L).build());

        ResponseEntity<ConversationSummaryResponse> response = chatController.createConversation(jwt, request);

        assertEquals(HttpStatus.CREATED, response.getStatusCode());
        assertEquals(9L, response.getBody().getConversationId());
    }

    @Test
    void shouldReturnBadRequestWhenCreateConversationValidationFails() {
        CreateConversationRequest request = CreateConversationRequest.builder().vacancyId(1L).candidateId(20L).build();
        when(chatService.startConversation(10L, 1L, 20L)).thenThrow(new IllegalArgumentException("bad"));

        ResponseEntity<ConversationSummaryResponse> response = chatController.createConversation(jwt, request);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
    }

    @Test
    void shouldReturnNotFoundWhenCreateConversationRuntimeFails() {
        CreateConversationRequest request = CreateConversationRequest.builder().vacancyId(1L).candidateId(20L).build();
        when(chatService.startConversation(10L, 1L, 20L)).thenThrow(new RuntimeException("not found"));

        ResponseEntity<ConversationSummaryResponse> response = chatController.createConversation(jwt, request);

        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
    }

    @Test
    void shouldReturnBadRequestForInvalidMessageLimit() {
        ResponseEntity<List<ChatMessageResponse>> response = chatController.getMessages(jwt, 1L, 500);
        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
    }

    @Test
    void shouldReturnBadRequestForNullMessageLimit() {
        ResponseEntity<List<ChatMessageResponse>> response = chatController.getMessages(jwt, 1L, null);
        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
    }

    @Test
    void shouldGetMessages() {
        ChatMessageResponse message = ChatMessageResponse.builder().id(11L).build();
        when(chatService.getConversationMessages(10L, 7L, 20)).thenReturn(List.of(message));

        ResponseEntity<List<ChatMessageResponse>> response = chatController.getMessages(jwt, 7L, 20);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(11L, response.getBody().get(0).getId());
    }

    @Test
    void shouldReturnNotFoundWhenGetMessagesFails() {
        when(chatService.getConversationMessages(10L, 7L, 20)).thenThrow(new RuntimeException("x"));

        ResponseEntity<List<ChatMessageResponse>> response = chatController.getMessages(jwt, 7L, 20);

        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
    }

    @Test
    void shouldSendMessage() {
        ChatMessageRequest request = ChatMessageRequest.builder().content("hello").clientMessageId("c1").build();
        when(chatService.sendMessage(10L, 8L, "hello", "c1"))
                .thenReturn(ChatMessageResponse.builder().id(100L).build());

        ResponseEntity<ChatMessageResponse> response = chatController.sendMessage(jwt, 8L, request);

        assertEquals(HttpStatus.CREATED, response.getStatusCode());
        assertEquals(100L, response.getBody().getId());
    }

    @Test
    void shouldReturnBadRequestWhenSendMessageInvalid() {
        ChatMessageRequest request = ChatMessageRequest.builder().content("hello").build();
        when(chatService.sendMessage(eq(10L), eq(8L), eq("hello"), eq(null)))
                .thenThrow(new IllegalArgumentException("bad"));

        ResponseEntity<ChatMessageResponse> response = chatController.sendMessage(jwt, 8L, request);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
    }

    @Test
    void shouldReturnNotFoundWhenSendMessageRuntime() {
        ChatMessageRequest request = ChatMessageRequest.builder().content("hello").build();
        when(chatService.sendMessage(eq(10L), eq(8L), eq("hello"), eq(null)))
                .thenThrow(new RuntimeException("missing"));

        ResponseEntity<ChatMessageResponse> response = chatController.sendMessage(jwt, 8L, request);

        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
    }

    @Test
    void shouldMarkAsReadNoContent() {
        ResponseEntity<Void> response = chatController.markAsRead(jwt, 50L);

        assertEquals(HttpStatus.NO_CONTENT, response.getStatusCode());
        verify(chatService).markConversationAsRead(10L, 50L);
    }

    @Test
    void shouldMarkAsReadNotFoundWhenServiceFails() {
        org.mockito.Mockito.doThrow(new RuntimeException("x")).when(chatService).markConversationAsRead(anyLong(), eq(50L));

        ResponseEntity<Void> response = chatController.markAsRead(jwt, 50L);

        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
    }
}
