package ieti.jobswipe.service.chat;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpEntity;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestTemplate;

import ieti.jobswipe.dto.chat.ChatRealtimeEventResponse;

@ExtendWith(MockitoExtension.class)
class ChatRealtimeServiceTest {

    @Mock
    private RestTemplate restTemplate;

    private ChatRealtimeService service;

    @BeforeEach
    void setUp() {
        service = new ChatRealtimeService(restTemplate);
        ReflectionTestUtils.setField(service, "supabaseRealtimeEnabled", true);
        ReflectionTestUtils.setField(service, "supabaseUrl", "https://example.supabase.co/");
        ReflectionTestUtils.setField(service, "supabaseServiceRoleKey", "key-123");
        ReflectionTestUtils.setField(service, "realtimeNotificationsTable", "notification_events_realtime");
    }

    @Test
    void shouldPublishEventWhenConfigured() {
        ChatRealtimeEventResponse event = ChatRealtimeEventResponse.builder()
                .type("NEW_MESSAGE")
                .payload(Map.of("conversationId", 10L))
                .build();

        service.publishToUser(77L, event);

        verify(restTemplate, times(1)).postForEntity(
                eq("https://example.supabase.co/rest/v1/notification_events_realtime"),
                any(HttpEntity.class),
                eq(Void.class));
    }

    @Test
    void shouldNotPublishWhenMissingType() {
        ChatRealtimeEventResponse event = ChatRealtimeEventResponse.builder().type(" ").build();

        service.publishToUser(77L, event);

        verify(restTemplate, never()).postForEntity(any(String.class), any(HttpEntity.class), eq(Void.class));
    }

    @Test
    void shouldNotPublishWhenNotConfigured() {
        ReflectionTestUtils.setField(service, "supabaseRealtimeEnabled", false);
        ChatRealtimeEventResponse event = ChatRealtimeEventResponse.builder().type("X").payload(Map.of()).build();

        service.publishToUser(77L, event);

        verify(restTemplate, never()).postForEntity(any(String.class), any(HttpEntity.class), eq(Void.class));
    }

    @Test
    void shouldPublishWhenPayloadIsNullUsingEmptyPayloadFallback() {
        ChatRealtimeEventResponse event = ChatRealtimeEventResponse.builder().type("X").payload(null).build();

        service.publishToUser(77L, event);

        verify(restTemplate, times(1)).postForEntity(
                eq("https://example.supabase.co/rest/v1/notification_events_realtime"),
                any(HttpEntity.class),
                eq(Void.class));
    }

    @Test
    void shouldHandleBlankSupabaseUrlAndStripTrailingSlash() {
        assertEquals("", ReflectionTestUtils.invokeMethod(service, "stripTrailingSlash", (Object) null));
        assertEquals("", ReflectionTestUtils.invokeMethod(service, "stripTrailingSlash", ""));
    }

    @Test
    void shouldSwallowRuntimeErrorsFromRestTemplate() {
        ChatRealtimeEventResponse event = ChatRealtimeEventResponse.builder().type("X").payload(Map.of()).build();
        org.mockito.Mockito.doThrow(new RuntimeException("boom"))
                .when(restTemplate)
                .postForEntity(any(String.class), any(HttpEntity.class), eq(Void.class));

        service.publishToUser(77L, event);

        verify(restTemplate, times(1)).postForEntity(any(String.class), any(HttpEntity.class), eq(Void.class));
    }

    @Test
    void shouldNotPublishWhenUserIdIsNull() {
        ChatRealtimeEventResponse event = ChatRealtimeEventResponse.builder().type("X").payload(Map.of()).build();

        service.publishToUser(null, event);

        verify(restTemplate, never()).postForEntity(any(String.class), any(HttpEntity.class), eq(Void.class));
    }

    @Test
    void shouldNotPublishWhenEventIsNull() {
        service.publishToUser(77L, null);

        verify(restTemplate, never()).postForEntity(any(String.class), any(HttpEntity.class), eq(Void.class));
    }

    @ParameterizedTest
    @ValueSource(strings = { "supabaseUrl", "supabaseServiceRoleKey", "realtimeNotificationsTable" })
    void shouldNotPublishWhenRequiredConfigurationFieldIsBlank(String fieldName) {
        ReflectionTestUtils.setField(service, fieldName, " ");
        ChatRealtimeEventResponse event = ChatRealtimeEventResponse.builder().type("X").payload(Map.of()).build();

        service.publishToUser(77L, event);

        verify(restTemplate, never()).postForEntity(any(String.class), any(HttpEntity.class), eq(Void.class));
    }

    @Test
    void shouldReturnUrlUnchangedWhenItHasNoTrailingSlash() {
        assertEquals(
                "https://example.supabase.co",
                ReflectionTestUtils.invokeMethod(service, "stripTrailingSlash", "https://example.supabase.co"));
    }
}
