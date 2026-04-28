package ieti.jobswipe.config;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.CacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.serializer.GenericJacksonJsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;

@Configuration
public class CacheConfig {

    @Value("${app.cache.ttl.vacancies-seconds:60}")
    private long vacanciesTtlSeconds;

    @Value("${app.cache.ttl.company-activity-seconds:45}")
    private long companyActivityTtlSeconds;

    @Value("${app.cache.ttl.company-pipeline-seconds:45}")
    private long companyPipelineTtlSeconds;

    @Value("${app.cache.ttl.applicants-seconds:45}")
    private long applicantsTtlSeconds;

    @Value("${app.cache.ttl.matches-seconds:30}")
    private long matchesTtlSeconds;

    @Bean
    public CacheManager cacheManager(RedisConnectionFactory redisConnectionFactory) {
        RedisCacheConfiguration defaultConfig = baseConfig(Duration.ofSeconds(30));

        Map<String, RedisCacheConfiguration> cacheConfigurations = new HashMap<>();
        cacheConfigurations.put("vacanciesForUser", baseConfig(Duration.ofSeconds(Math.max(10, vacanciesTtlSeconds))));
        cacheConfigurations.put("companyActivity", baseConfig(Duration.ofSeconds(Math.max(10, companyActivityTtlSeconds))));
        cacheConfigurations.put("companyPipeline", baseConfig(Duration.ofSeconds(Math.max(10, companyPipelineTtlSeconds))));
        cacheConfigurations.put("vacancyApplicants", baseConfig(Duration.ofSeconds(Math.max(10, applicantsTtlSeconds))));
        cacheConfigurations.put("userMatches", baseConfig(Duration.ofSeconds(Math.max(10, matchesTtlSeconds))));

        return RedisCacheManager.builder(redisConnectionFactory)
                .cacheDefaults(defaultConfig)
                .withInitialCacheConfigurations(cacheConfigurations)
                .build();
    }

    private RedisCacheConfiguration baseConfig(Duration ttl) {
        GenericJacksonJsonRedisSerializer serializer = GenericJacksonJsonRedisSerializer
            .builder()
            .build();

        return RedisCacheConfiguration.defaultCacheConfig()
                .entryTtl(ttl)
                .disableCachingNullValues()
                .serializeValuesWith(
                        RedisSerializationContext.SerializationPair
                .fromSerializer(serializer));
    }
}
