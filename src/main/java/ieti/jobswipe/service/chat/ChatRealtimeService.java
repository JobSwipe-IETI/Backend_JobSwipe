package ieti.jobswipe.service.chat;

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
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestTemplate;

import ieti.jobswipe.dto.chat.ChatRealtimeEventResponse;

@Service
public class ChatRealtimeService {

    private static final Logger logger = LoggerFactory.getLogger(ChatRealtimeService.class);

    private final RestTemplate restTemplate;

    @Value("${app.supabase.realtime.enabled:true}")
    private boolean supabaseRealtimeEnabled;

    @Value("${app.supabase.url:}")
    private String supabaseUrl;

    @Value("${app.supabase.service-role-key:}")
    private String supabaseServiceRoleKey;

    @Value("${app.supabase.realtime.notifications-table:notification_events_realtime}")
    private String realtimeNotificationsTable;

    public ChatRealtimeService(@Qualifier("supabaseRealtimeRestTemplate") RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }

    @Async("taskExecutor")
    public void publishToUser(Long userId, ChatRealtimeEventResponse event) {
        if (!isConfigured() || userId == null || event == null || !StringUtils.hasText(event.getType())) {
            return;
        }

        String endpoint = String.format("%s/rest/v1/%s", stripTrailingSlash(supabaseUrl), realtimeNotificationsTable);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("apikey", supabaseServiceRoleKey);
        headers.set("Authorization", "Bearer " + supabaseServiceRoleKey);
        headers.set("Prefer", "return=minimal");

        Map<String, Object> row = new HashMap<>();
        row.put("user_id", userId);
        row.put("type", event.getType());
        row.put("payload", event.getPayload() != null ? event.getPayload() : Map.of());

        try {
            restTemplate.postForEntity(endpoint, new HttpEntity<>(List.of(row), headers), Void.class);
        } catch (RuntimeException ex) {
            logger.warn("Supabase notification publish failed for userId={} type={}", userId, event.getType(), ex);
        }
    }

    private boolean isConfigured() {
        return supabaseRealtimeEnabled
                && StringUtils.hasText(supabaseUrl)
                && StringUtils.hasText(supabaseServiceRoleKey)
                && StringUtils.hasText(realtimeNotificationsTable);
    }

    private String stripTrailingSlash(String value) {
        if (!StringUtils.hasText(value)) {
            return "";
        }
        return value.endsWith("/") ? value.substring(0, value.length() - 1) : value;
    }
}
