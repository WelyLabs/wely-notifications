package com.calendar.notifications.configuration;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Names the API that springdoc generates from the controllers. */
@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI openApi() {
        return new OpenAPI().info(new Info()
                .title("wely-notifications")
                .version("v1")
                .description("Notification center and Web Push. Calls, messages, event dates and friend requests "
                        + "arrive from Kafka; clients read them over REST and SSE, devices receive them as push."));
    }
}
