package com.calendar.notifications.configuration;

import com.calendar.notifications.exception.RequestIds;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.reactive.function.client.ClientRequest;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;
import reactor.util.context.Context;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class PushClientConfigTest {

    @Test
    @DisplayName("the push client is built without a base URL")
    void pushWebClient_shouldBeBuilt() {
        assertThat(new PushClientConfig().pushWebClient()).isNotNull();
    }

    @Test
    @DisplayName("the APNs client is built, HTTP/2 over TLS")
    void apnsWebClient_shouldBeBuilt() {
        assertThat(new PushClientConfig().apnsWebClient()).isNotNull();
    }

    /** What an external push service would receive, the request id in the Reactor Context or not. */
    private static ClientRequest sentBy(WebClient client) {
        List<ClientRequest> sent = new ArrayList<>();
        WebClient stubbed = client.mutate()
                .exchangeFunction(request -> {
                    sent.add(request);
                    return Mono.just(ClientResponse.create(HttpStatus.CREATED).build());
                })
                .build();
        StepVerifier.create(stubbed.post().uri("https://push.example/endpoint").retrieve().toBodilessEntity()
                        .contextWrite(Context.of(RequestIds.REQUEST_ID_ATTRIBUTE, "abcd1234-ef56")))
                .expectNextCount(1)
                .expectComplete().verify(Duration.ofSeconds(10));
        return sent.getFirst();
    }

    @Test
    @DisplayName("a Web Push service is external: it never receives our request id")
    void pushWebClient_shouldNotForwardTheRequestId() {
        assertThat(sentBy(new PushClientConfig().pushWebClient()).headers()
                .containsHeader(RequestIds.REQUEST_ID_HEADER)).isFalse();
    }

    @Test
    @DisplayName("APNs is external: it never receives our request id")
    void apnsWebClient_shouldNotForwardTheRequestId() {
        assertThat(sentBy(new PushClientConfig().apnsWebClient()).headers()
                .containsHeader(RequestIds.REQUEST_ID_HEADER)).isFalse();
    }
}
