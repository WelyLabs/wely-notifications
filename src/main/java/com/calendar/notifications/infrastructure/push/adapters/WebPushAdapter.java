package com.calendar.notifications.infrastructure.push.adapters;

import com.calendar.notifications.configuration.PushProperties;
import com.calendar.notifications.domain.models.PushMessage;
import com.calendar.notifications.domain.models.PushResult;
import com.calendar.notifications.domain.models.PushSubscription;
import com.calendar.notifications.domain.ports.PushSender;
import com.calendar.notifications.infrastructure.push.crypto.VapidSigner;
import com.calendar.notifications.infrastructure.push.crypto.WebPushEncryption;
import com.calendar.notifications.infrastructure.push.mappers.PushPayloadMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import tools.jackson.databind.ObjectMapper;

import java.net.URI;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.util.Base64;

/**
 * Web Push with the JDK alone: the payload encrypted for the browser (RFC 8291), the request signed
 * for the push service (RFC 8292 VAPID), and one POST to the subscription's endpoint.
 */
@Component
public class WebPushAdapter implements PushSender {

    private static final Logger log = LoggerFactory.getLogger(WebPushAdapter.class);
    private static final String AES128GCM = "aes128gcm";

    private final WebClient webClient;
    private final PushPayloadMapper payloadMapper;
    private final ObjectMapper objectMapper;
    private final WebPushEncryption encryption;
    private final VapidSigner signer;
    private final String publicKey;
    private final Duration timeout;

    public WebPushAdapter(PushProperties properties, @Qualifier("pushWebClient") WebClient webClient,
                          PushPayloadMapper payloadMapper, ObjectMapper objectMapper, Clock clock) {
        this.webClient = webClient;
        this.payloadMapper = payloadMapper;
        this.objectMapper = objectMapper;
        this.encryption = new WebPushEncryption(new SecureRandom());
        this.timeout = properties.timeout();
        this.signer = signerOf(properties.vapid(), objectMapper, clock);
        this.publicKey = signer == null ? "" : properties.vapid().publicKey();
    }

    @Override
    public boolean enabled() {
        return signer != null;
    }

    @Override
    public String publicKey() {
        return publicKey;
    }

    @Override
    public Mono<PushResult> send(PushSubscription subscription, PushMessage message) {
        if (!enabled()) {
            return Mono.just(PushResult.FAILED);
        }
        return Mono.fromCallable(() -> encrypt(subscription, message))
                .flatMap(body -> post(subscription.endpoint(), message, body))
                .timeout(timeout)
                .onErrorResume(error -> {
                    // The endpoint is not logged: it is a capability to push to someone's device.
                    log.warn("Push failed for user {}: {}", subscription.userId(), error.toString());
                    return Mono.just(PushResult.FAILED);
                });
    }

    private byte[] encrypt(PushSubscription subscription, PushMessage message) {
        byte[] payload = objectMapper.writeValueAsBytes(payloadMapper.toPayload(message));
        return encryption.encrypt(payload, Base64.getUrlDecoder().decode(subscription.p256dh()),
                Base64.getUrlDecoder().decode(subscription.auth()));
    }

    private Mono<PushResult> post(String endpoint, PushMessage message, byte[] body) {
        return webClient.post()
                .uri(URI.create(endpoint))
                .header("Authorization", signer.authorization(endpoint))
                .header("Content-Encoding", AES128GCM)
                .header("TTL", String.valueOf(message.ttlSeconds()))
                .header("Urgency", message.urgency().name().toLowerCase())
                .headers(headers -> {
                    if (message.topic() != null) {
                        headers.set("Topic", message.topic());
                    }
                })
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .bodyValue(body)
                .exchangeToMono(response -> response.releaseBody().thenReturn(resultOf(response.statusCode())));
    }

    /** 404 and 410 mean the browser unsubscribed or the subscription expired: forget it. */
    static PushResult resultOf(HttpStatusCode status) {
        if (status.is2xxSuccessful()) {
            return PushResult.DELIVERED;
        }
        if (status.value() == HttpStatus.NOT_FOUND.value() || status.value() == HttpStatus.GONE.value()) {
            return PushResult.GONE;
        }
        log.warn("Push service answered {}", status.value());
        return PushResult.FAILED;
    }

    private static VapidSigner signerOf(PushProperties.Vapid vapid, ObjectMapper objectMapper, Clock clock) {
        if (!vapid.configured()) {
            log.warn("No VAPID key pair configured: Web Push is off, notifications are only recorded and streamed");
            return null;
        }
        try {
            return new VapidSigner(vapid.publicKey(), vapid.privateKey(), vapid.subject(), objectMapper, clock);
        } catch (IllegalArgumentException invalid) {
            log.error("The VAPID key pair is malformed: Web Push is off ({})", invalid.getMessage());
            return null;
        }
    }
}
