package com.calendar.notifications.configuration;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.netty.http.HttpProtocol;
import reactor.netty.http.client.HttpClient;

/**
 * The HTTP clients push services are called with: no base URL, each subscription has its own.
 * Spring Boot 4 no longer auto-configures a WebClient.Builder.
 */
@Configuration
public class PushClientConfig {

    @Bean
    public WebClient pushWebClient() {
        return WebClient.builder().build();
    }

    /** APNs speaks HTTP/2 only, over TLS with ALPN, so this client never falls back to HTTP/1.1. */
    @Bean
    public WebClient apnsWebClient() {
        HttpClient http = HttpClient.create().protocol(HttpProtocol.H2).secure();
        return WebClient.builder().clientConnector(new ReactorClientHttpConnector(http)).build();
    }
}
