package com.calendar.notifications.exception;

import org.junit.jupiter.api.Test;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;

import static org.assertj.core.api.Assertions.assertThat;

class RequestIdsTest {

    @Test
    void of_shouldReadTheChosenId_fromTheExchangeAttribute() {
        MockServerWebExchange exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/x"));
        exchange.getAttributes().put(RequestIds.REQUEST_ID_ATTRIBUTE, "gateway-id-1234");

        assertThat(RequestIds.of(exchange)).isEqualTo("gateway-id-1234");
    }

    @Test
    void of_shouldFallBackToTheWebFluxId_whenNoIdWasChosen() {
        MockServerWebExchange exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/x"));

        assertThat(RequestIds.of(exchange)).isEqualTo(exchange.getRequest().getId());
    }
}
