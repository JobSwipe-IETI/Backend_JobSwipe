package ieti.jobswipe.config;

import java.time.Duration;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;

@Configuration
public class RestTemplateConfig {

    @Value("${app.supabase.realtime.connect-timeout-ms:1500}")
    private int supabaseRealtimeConnectTimeoutMs;

    @Value("${app.supabase.realtime.read-timeout-ms:2500}")
    private int supabaseRealtimeReadTimeoutMs;

    @Bean
    @Primary
    public RestTemplate restTemplate() {
        return new RestTemplate();
    }

    @Bean(name = "supabaseRealtimeRestTemplate")
    public RestTemplate supabaseRealtimeRestTemplate() {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(Duration.ofMillis(supabaseRealtimeConnectTimeoutMs));
        requestFactory.setReadTimeout(Duration.ofMillis(supabaseRealtimeReadTimeoutMs));
        return new RestTemplate(requestFactory);
    }
}

