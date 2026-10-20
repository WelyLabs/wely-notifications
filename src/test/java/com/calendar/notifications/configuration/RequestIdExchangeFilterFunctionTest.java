package com.calendar.notifications.configuration;

import com.calendar.notifications.exception.RequestIds;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.reactive.function.client.ClientRequest;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.ExchangeFunction;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;
import reactor.util.context.Context;

import java.net.URI;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.HttpMethod.GET;

class RequestIdExchangeFilterFunctionTest {

    private final RequestIdExchangeFilterFunction filter = new RequestIdExchangeFilterFunction();
    private final AtomicReference<ClientRequest> sent = new AtomicReference<>();
    private final ExchangeFunction next = request -> {
        sent.set(request);
        return Mono.just(ClientResponse.create(HttpStatus.OK).build());
    };

    private static ClientRequest request() {
        return ClientRequest.create(GET, URI.create("http://wely-users/user-service/internal/deleted-users")).build();
    }

    @Test
    void filter_shouldForwardTheRequestId_fromTheReactorContext() {
        StepVerifier.create(filter.filter(request(), next)
                        .contextWrite(Context.of(RequestIds.REQUEST_ID_ATTRIBUTE, "abcd1234-ef56")))
                .expectNextCount(1)
                .expectComplete().verify(Duration.ofSeconds(10));

        assertThat(sent.get().headers().getFirst(RequestIds.REQUEST_ID_HEADER)).isEqualTo("abcd1234-ef56");
    }

    @Test
    void filter_shouldLeaveTheRequestUnchanged_withoutARequestId() {
        StepVerifier.create(filter.filter(request(), next))
                .expectNextCount(1)
                .expectComplete().verify(Duration.ofSeconds(10));

        assertThat(sent.get().headers().containsHeader(RequestIds.REQUEST_ID_HEADER)).isFalse();
    }
}
