package ieti.jobswipe.service.chat;

import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestTemplate;

import ieti.jobswipe.dto.chat.ChatMessageResponse;

@Service
public class SupabaseChatRealtimePublisher {

    private static final Logger logger = LoggerFactory.getLogger(SupabaseChatRealtimePublisher.class);
    private static final DateTimeFormatter ISO_LOCAL_DATE_TIME = DateTimeFormatter.ISO_LOCAL_DATE_TIME;

    private final RestTemplate restTemplate;

    @Value("${app.supabase.realtime.enabled:true}")
    private boolean supabaseRealtimeEnabled;

    @Value("${app.supabase.url:}")
    private String supabaseUrl;

    @Value("${app.supabase.service-role-key:}")
    private String supabaseServiceRoleKey;

    @Value("${app.supabase.realtime.messages-table:chat_messages_realtime}")
    private String realtimeMessagesTable;

    public SupabaseChatRealtimePublisher(@Qualifier("supabaseRealtimeRestTemplate") RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }

    public void publishMessage(ChatMessageResponse message) {
        if (!isConfigured() || message == null || message.getId() == null) {
            return;
        }

        String endpoint = String.format("%s/rest/v1/%s", stripTrailingSlash(supabaseUrl), realtimeMessagesTable);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("apikey", supabaseServiceRoleKey);
        headers.set("Authorization", "Bearer " + supabaseServiceRoleKey);
        headers.set("Prefer", "return=minimal");

        Map<String, Object> row = new HashMap<>();
        row.put("message_id", message.getId());
        row.put("conversation_id", message.getConversationId());
        row.put("sender_id", message.getSenderId());
        row.put("sender_role", message.getSenderRole());
        row.put("content", message.getContent());
        row.put(
            "created_at",
            message.getCreatedAt() != null ? message.getCreatedAt().format(ISO_LOCAL_DATE_TIME) : null);
        row.put("client_message_id", message.getClientMessageId());

        try {
            restTemplate.postForEntity(endpoint, new HttpEntity<>(List.of(row), headers), Void.class);
        } catch (RuntimeException ex) {
            logger.warn("Supabase realtime publish failed for messageId={}", message.getId(), ex);
        }
    }

    private boolean isConfigured() {
        return supabaseRealtimeEnabled
                && StringUtils.hasText(supabaseUrl)
                && StringUtils.hasText(supabaseServiceRoleKey)
                && StringUtils.hasText(realtimeMessagesTable);
    }

    private String stripTrailingSlash(String value) {
        if (!StringUtils.hasText(value)) {
            return "";
        }
        return value.endsWith("/") ? value.substring(0, value.length() - 1) : value;
    }
}
