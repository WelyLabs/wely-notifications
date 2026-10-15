package com.calendar.notifications.configuration;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.client.WebClient;

/**
 * The HTTP client push services are called with: no base URL, each subscription has its own.
 * Spring Boot 4 no longer auto-configures a WebClient.Builder.
 */
@Configuration
public class PushClientConfig {

    @Bean
    public WebClient pushWebClient() {
        return WebClient.builder().build();
    }
}
