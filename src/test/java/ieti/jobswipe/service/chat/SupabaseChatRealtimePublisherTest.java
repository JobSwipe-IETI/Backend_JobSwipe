package ieti.jobswipe.service.chat;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import java.time.LocalDateTime;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpEntity;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestTemplate;

import ieti.jobswipe.dto.chat.ChatMessageResponse;

@ExtendWith(MockitoExtension.class)
class SupabaseChatRealtimePublisherTest {

    @Mock
    private RestTemplate restTemplate;

    private SupabaseChatRealtimePublisher publisher;

    @BeforeEach
    void setUp() {
        publisher = new SupabaseChatRealtimePublisher(restTemplate);
        ReflectionTestUtils.setField(publisher, "supabaseRealtimeEnabled", true);
        ReflectionTestUtils.setField(publisher, "supabaseUrl", "https://example.supabase.co/");
        ReflectionTestUtils.setField(publisher, "supabaseServiceRoleKey", "key-abc");
        ReflectionTestUtils.setField(publisher, "realtimeMessagesTable", "chat_messages_realtime");
    }

    @Test
    void shouldPublishMessageWhenConfigured() {
        ChatMessageResponse message = ChatMessageResponse.builder()
                .id(1L)
                .conversationId(2L)
                .senderId(3L)
                .senderRole("COMPANY")
                .content("hello")
                .createdAt(LocalDateTime.now())
                .clientMessageId("cli-1")
                .build();

        publisher.publishMessage(message);

        verify(restTemplate, times(1)).postForEntity(
                eq("https://example.supabase.co/rest/v1/chat_messages_realtime"),
                any(HttpEntity.class),
                eq(Void.class));
    }

    @Test
    void shouldNotPublishWhenMessageHasNoId() {
        ChatMessageResponse message = ChatMessageResponse.builder().id(null).build();

        publisher.publishMessage(message);

        verify(restTemplate, never()).postForEntity(any(String.class), any(HttpEntity.class), eq(Void.class));
    }

    @Test
    void shouldNotPublishWhenConfigurationMissing() {
        ReflectionTestUtils.setField(publisher, "supabaseServiceRoleKey", " ");
        ChatMessageResponse message = ChatMessageResponse.builder().id(9L).build();

        publisher.publishMessage(message);

        verify(restTemplate, never()).postForEntity(any(String.class), any(HttpEntity.class), eq(Void.class));
    }

    @Test
    void shouldNotPublishWhenUrlIsBlank() {
        ReflectionTestUtils.setField(publisher, "supabaseUrl", " ");
        ChatMessageResponse message = ChatMessageResponse.builder().id(13L).build();

        publisher.publishMessage(message);

        verify(restTemplate, never()).postForEntity(any(String.class), any(HttpEntity.class), eq(Void.class));
    }

    @Test
    void shouldNotPublishWhenMessagesTableIsBlank() {
        ReflectionTestUtils.setField(publisher, "realtimeMessagesTable", " ");
        ChatMessageResponse message = ChatMessageResponse.builder().id(14L).build();

        publisher.publishMessage(message);

        verify(restTemplate, never()).postForEntity(any(String.class), any(HttpEntity.class), eq(Void.class));
    }

    @Test
    void shouldSwallowRuntimeErrorsFromRestTemplate() {
        ChatMessageResponse message = ChatMessageResponse.builder().id(10L).build();
        org.mockito.Mockito.doThrow(new RuntimeException("boom"))
                .when(restTemplate)
                .postForEntity(any(String.class), any(HttpEntity.class), eq(Void.class));

        publisher.publishMessage(message);

        verify(restTemplate, times(1)).postForEntity(any(String.class), any(HttpEntity.class), eq(Void.class));
    }

    @Test
    void shouldNotPublishWhenMessageIsNull() {
        publisher.publishMessage(null);

        verify(restTemplate, never()).postForEntity(any(String.class), any(HttpEntity.class), eq(Void.class));
    }

    @Test
    void shouldNotPublishWhenRealtimeDisabled() {
        ReflectionTestUtils.setField(publisher, "supabaseRealtimeEnabled", false);
        ChatMessageResponse message = ChatMessageResponse.builder().id(11L).build();

        publisher.publishMessage(message);

        verify(restTemplate, never()).postForEntity(any(String.class), any(HttpEntity.class), eq(Void.class));
    }

    @Test
    void shouldStripTrailingSlashFromConfiguredUrl() {
        ChatMessageResponse message = ChatMessageResponse.builder().id(12L).build();

        publisher.publishMessage(message);

        verify(restTemplate, times(1)).postForEntity(
                eq("https://example.supabase.co/rest/v1/chat_messages_realtime"),
                any(HttpEntity.class),
                eq(Void.class));
    }

    @Test
    void shouldLeaveUrlUnchangedWhenItHasNoTrailingSlash() {
        assertEquals(
                "https://example.supabase.co",
                ReflectionTestUtils.invokeMethod(publisher, "stripTrailingSlash", "https://example.supabase.co"));
    }

    @Test
    void shouldReturnEmptyStringWhenUrlIsBlankOrNull() {
        assertEquals("", ReflectionTestUtils.invokeMethod(publisher, "stripTrailingSlash", (Object) null));
        assertEquals("", ReflectionTestUtils.invokeMethod(publisher, "stripTrailingSlash", "   "));
    }
}
