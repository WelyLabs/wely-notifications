package com.calendar.notifications.configuration;

import com.calendar.notifications.exception.RequestIds;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class RequestIdWebFilterTest {

    private static final String GATEWAY_ID = "3f2b8c1e-9d4a-4c55-8e21-7a6b5c4d3e2f";

    private final RequestIdWebFilter filter = new RequestIdWebFilter();
    private final AtomicReference<String> seenInContext = new AtomicReference<>();

    /** Records what a downstream call would read from the Reactor Context. */
    private final WebFilterChain chain = exchange -> Mono.deferContextual(context -> {
        seenInContext.set(context.<String>getOrEmpty(RequestIds.REQUEST_ID_ATTRIBUTE).orElse(null));
        return Mono.empty();
    });

    private MockServerWebExchange filtered(MockServerHttpRequest.BaseBuilder<?> request) {
        MockServerWebExchange exchange = MockServerWebExchange.from(request);
        StepVerifier.create(filter.filter(exchange, chain)).expectComplete().verify(Duration.ofSeconds(10));
        return exchange;
    }

    @Test
    @DisplayName("the gateway's id is kept: attribute, Reactor Context and response header")
    void filter_shouldKeepAValidGatewayId() {
        MockServerWebExchange exchange =
                filtered(MockServerHttpRequest.get("/x").header(RequestIds.REQUEST_ID_HEADER, GATEWAY_ID));

        assertThat(RequestIds.of(exchange)).isEqualTo(GATEWAY_ID);
        assertThat(seenInContext.get()).isEqualTo(GATEWAY_ID);
        assertThat(exchange.getResponse().getHeaders().getFirst(RequestIds.REQUEST_ID_HEADER)).isEqualTo(GATEWAY_ID);
    }

    @ParameterizedTest
    @ValueSource(strings = {"short", "has spaces in it", "line\\nbreak-123", "semi;colon-1234", "é-accented-1234"})
    @DisplayName("a header that is not a plain 8-64 character token falls back to WebFlux's id")
    void filter_shouldFallBack_whenTheHeaderIsInvalid(String received) {
        MockServerWebExchange exchange =
                filtered(MockServerHttpRequest.get("/x").header(RequestIds.REQUEST_ID_HEADER, received));

        assertThat(RequestIds.of(exchange)).isEqualTo(exchange.getRequest().getId());
        assertThat(seenInContext.get()).isEqualTo(exchange.getRequest().getId());
        assertThat(exchange.getResponse().getHeaders().getFirst(RequestIds.REQUEST_ID_HEADER))
                .isEqualTo(exchange.getRequest().getId());
    }

    @Test
    void filter_shouldFallBack_whenTheHeaderIsOversized() {
        MockServerWebExchange exchange =
                filtered(MockServerHttpRequest.get("/x").header(RequestIds.REQUEST_ID_HEADER, "a".repeat(65)));

        assertThat(RequestIds.of(exchange)).isEqualTo(exchange.getRequest().getId());
    }

    @Test
    void filter_shouldFallBack_whenThereIsNoHeader() {
        MockServerWebExchange exchange = filtered(MockServerHttpRequest.get("/x"));

        assertThat(RequestIds.of(exchange)).isEqualTo(exchange.getRequest().getId());
        assertThat(exchange.getResponse().getHeaders().getFirst(RequestIds.REQUEST_ID_HEADER))
                .isEqualTo(exchange.getRequest().getId());
    }
}
