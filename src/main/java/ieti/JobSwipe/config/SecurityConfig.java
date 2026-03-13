package ieti.JobSwipe.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

import ieti.JobSwipe.security.GoogleIdTokenAuthenticationFilter;
import ieti.JobSwipe.security.RestAuthenticationEntryPoint;

@Configuration
@EnableWebSecurity
@EnableConfigurationProperties(GoogleOAuthProperties.class)
public class SecurityConfig {

    private static final String[] PUBLIC_ENDPOINTS = {
            "/api/health",
            "/swagger-ui.html",
            "/swagger-ui/**",
            "/v3/api-docs/**"
    };

    @Bean
        public SecurityFilterChain securityFilterChain(HttpSecurity http,
                                                                                                   GoogleIdTokenAuthenticationFilter googleIdTokenAuthenticationFilter,
                                                                                                   RestAuthenticationEntryPoint restAuthenticationEntryPoint) throws Exception {
        return http
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                                .exceptionHandling(exceptions -> exceptions.authenticationEntryPoint(restAuthenticationEntryPoint))
                .authorizeHttpRequests(authorize -> authorize
                        .requestMatchers(PUBLIC_ENDPOINTS).permitAll()
                        .anyRequest().permitAll())
                                .addFilterBefore(googleIdTokenAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)
                .build();
    }
}