package com.calendar.notifications.configuration;

import com.calendar.notifications.exception.RequestIds;
import org.springframework.web.reactive.function.client.ClientRequest;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.ExchangeFilterFunction;
import org.springframework.web.reactive.function.client.ExchangeFunction;
import reactor.core.publisher.Mono;

/**
 * Forwards the current request id to another Wely service as {@code X-Request-Id}, read from the
 * Reactor Context {@link RequestIdWebFilter} filled. Without one — a background call such as the
 * reconciliation loop's — the request goes out unchanged.
 *
 * <p>For Wely clients only: an external service (APNs, a Web Push service) has no use for our ids.
 */
public class RequestIdExchangeFilterFunction implements ExchangeFilterFunction {

    @Override
    public Mono<ClientResponse> filter(ClientRequest request, ExchangeFunction next) {
        return Mono.deferContextual(context -> context.<String>getOrEmpty(RequestIds.REQUEST_ID_ATTRIBUTE)
                .map(id -> next.exchange(ClientRequest.from(request)
                        .headers(headers -> headers.set(RequestIds.REQUEST_ID_HEADER, id))
                        .build()))
                .orElseGet(() -> next.exchange(request)));
    }
}
