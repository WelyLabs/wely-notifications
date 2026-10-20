package com.calendar.notifications.configuration;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.method.HandlerTypePredicate;
import org.springframework.web.reactive.config.PathMatchConfigurer;
import org.springframework.web.reactive.config.WebFluxConfigurer;

/**
 * This service's additions to Boot's WebFlux setup. No {@code @EnableWebFlux}: it would switch off
 * Boot's WebFlux auto-configuration, and with it every {@code spring.webflux.*} and
 * {@code spring.http.codecs.*} property and Boot's JSON mapper in the codecs.
 */
@Configuration
public class WebConfig implements WebFluxConfigurer {

    @Override
    public void configurePathMatching(PathMatchConfigurer configurer) {
        // Scoped to this service's own controllers by package, not by the @RestController
        // annotation: that predicate also matched springdoc's OpenApiWebfluxResource, which
        // moved the specification to /notifications-service/v3/api-docs and left /v3/api-docs a 404.
        configurer.addPathPrefix(
                "/notifications-service",
                HandlerTypePredicate.forBasePackage("com.calendar.notifications.application.rest"));
    }
}