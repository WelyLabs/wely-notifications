package com.calendar.notifications.infrastructure.push.adapters;

import com.calendar.notifications.configuration.PushProperties;
import com.calendar.notifications.domain.models.ApnsDevice;
import com.calendar.notifications.domain.models.ApnsEnvironment;
import com.calendar.notifications.domain.models.PushMessage;
import com.calendar.notifications.domain.models.PushResult;
import com.calendar.notifications.domain.models.PushUrgency;
import com.calendar.notifications.domain.models.VoipPush;
import com.calendar.notifications.domain.ports.NativePushSender;
import com.calendar.notifications.infrastructure.push.crypto.ApnsTokenProvider;
import com.calendar.notifications.infrastructure.push.mappers.ApnsPayloadMapper;
import com.calendar.notifications.infrastructure.push.mappers.VoipPayloadMapper;
import com.calendar.notifications.infrastructure.push.models.ApnsError;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import tools.jackson.databind.ObjectMapper;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.util.Set;
import java.util.concurrent.Callable;

/**
 * Native iOS push: one HTTP/2 POST per device to APNs, authenticated by a provider token signed with
 * the team's .p8 key, either an alert or a PushKit (VoIP) push. Off, with a single warning, while no
 * key is configured.
 */
@Component
public class ApnsAdapter implements NativePushSender {

    private static final Logger log = LoggerFactory.getLogger(ApnsAdapter.class);
    static final String PRODUCTION_HOST = "https://api.push.apple.com";
    static final String SANDBOX_HOST = "https://api.sandbox.push.apple.com";
    static final int MAX_COLLAPSE_ID_BYTES = 64;
    private static final String HIGH_PRIORITY = "10";
    private static final String NORMAL_PRIORITY = "5";
    private static final String PUSH_TYPE_ALERT = "alert";
    private static final String PUSH_TYPE_VOIP = "voip";
    // PushKit pushes go to the app's VoIP topic, the bundle id with this suffix.
    static final String VOIP_TOPIC_SUFFIX = ".voip";
    private static final String CALL_TAG_PREFIX = "call-";
    // Besides 410, the reasons APNs gives for a token that will never work again.
    private static final Set<String> GONE_REASONS = Set.of("BadDeviceToken", "Unregistered");

    private final WebClient webClient;
    private final ApnsPayloadMapper payloadMapper;
    private final VoipPayloadMapper voipPayloadMapper;
    private final ObjectMapper objectMapper;
    private final Clock clock;
    private final Duration timeout;
    private final String bundleId;
    private final ApnsTokenProvider tokens;

    public ApnsAdapter(PushProperties properties, @Qualifier("apnsWebClient") WebClient webClient,
                       ApnsPayloadMapper payloadMapper, VoipPayloadMapper voipPayloadMapper, ObjectMapper objectMapper,
                       Clock clock) {
        this.webClient = webClient;
        this.payloadMapper = payloadMapper;
        this.voipPayloadMapper = voipPayloadMapper;
        this.objectMapper = objectMapper;
        this.clock = clock;
        this.timeout = properties.timeout();
        this.bundleId = properties.apns().bundleId();
        this.tokens = tokensOf(properties.apns(), objectMapper, clock);
    }

    @Override
    public boolean enabled() {
        return tokens != null;
    }

    @Override
    public Mono<PushResult> send(ApnsDevice device, PushMessage message) {
        if (!enabled()) {
            return Mono.just(PushResult.FAILED);
        }
        return deliver(device, () -> new Request(bundleId, PUSH_TYPE_ALERT, priorityOf(message.urgency()),
                message.ttlSeconds(), collapseId(message.tag()),
                objectMapper.writeValueAsBytes(payloadMapper.toPayload(message))));
    }

    /** Always priority 10 and the call's 30-second life; the ring and its hang-up share one collapse id. */
    @Override
    public Mono<PushResult> sendVoip(ApnsDevice device, VoipPush push) {
        if (!enabled()) {
            return Mono.just(PushResult.FAILED);
        }
        return deliver(device, () -> new Request(bundleId + VOIP_TOPIC_SUFFIX, PUSH_TYPE_VOIP, HIGH_PRIORITY,
                VoipPush.TTL_SECONDS, collapseId(CALL_TAG_PREFIX + push.callId()),
                objectMapper.writeValueAsBytes(voipPayloadOf(push))));
    }

    private Object voipPayloadOf(VoipPush push) {
        return switch (push) {
            case VoipPush.Ring ring -> voipPayloadMapper.toPayload(ring);
            case VoipPush.Hangup hangup -> voipPayloadMapper.toPayload(hangup);
        };
    }

    private Mono<PushResult> deliver(ApnsDevice device, Callable<Request> request) {
        return Mono.fromCallable(request)
                .flatMap(built -> post(device, built))
                .timeout(timeout)
                .onErrorResume(error -> {
                    // The token is not logged: it is a capability to push to someone's phone.
                    log.warn("APNs push failed for user {}: {}", device.userId(), error.toString());
                    return Mono.just(PushResult.FAILED);
                });
    }

    /** What differs between an alert and a VoIP push; the collapse id is null when there is none. */
    private record Request(String topic, String pushType, String priority, long ttlSeconds, String collapseId,
                           byte[] body) {}

    private Mono<PushResult> post(ApnsDevice device, Request built) {
        WebClient.RequestBodySpec request = webClient.post()
                .uri(deviceUri(device))
                .header("authorization", "bearer " + tokens.token())
                .header("apns-topic", built.topic())
                .header("apns-push-type", built.pushType())
                .header("apns-priority", built.priority())
                .header("apns-expiration", String.valueOf(clock.instant().getEpochSecond() + built.ttlSeconds()))
                .contentType(MediaType.APPLICATION_JSON);
        if (built.collapseId() != null) {
            request = request.header("apns-collapse-id", built.collapseId());
        }
        return request.bodyValue(built.body())
                .exchangeToMono(response -> {
                    int status = response.statusCode().value();
                    if (response.statusCode().is2xxSuccessful()) {
                        return response.releaseBody().thenReturn(PushResult.DELIVERED);
                    }
                    return response.bodyToMono(String.class).defaultIfEmpty("")
                            .map(this::reasonOf)
                            .map(reason -> {
                                PushResult result = resultOf(status, reason);
                                if (result == PushResult.FAILED) {
                                    log.warn("APNs answered {} for user {}: {}", status, device.userId(), reason);
                                }
                                return result;
                            });
                });
    }

    /** 2xx is delivered; 410, or 400 for a bad or unregistered token, means the token is gone. */
    static PushResult resultOf(int status, String reason) {
        if (status >= HttpStatus.OK.value() && status < HttpStatus.MULTIPLE_CHOICES.value()) {
            return PushResult.DELIVERED;
        }
        if (status == HttpStatus.GONE.value()
                || (status == HttpStatus.BAD_REQUEST.value() && GONE_REASONS.contains(reason))) {
            return PushResult.GONE;
        }
        return PushResult.FAILED;
    }

    static URI deviceUri(ApnsDevice device) {
        String host = device.environment() == ApnsEnvironment.SANDBOX ? SANDBOX_HOST : PRODUCTION_HOST;
        return URI.create(host + "/3/device/" + device.token());
    }

    static String priorityOf(PushUrgency urgency) {
        return urgency == PushUrgency.HIGH ? HIGH_PRIORITY : NORMAL_PRIORITY;
    }

    /** The tag, cut to the 64 bytes APNs accepts without splitting a character; null when there is none. */
    static String collapseId(String tag) {
        if (tag == null || tag.isBlank()) {
            return null;
        }
        StringBuilder kept = new StringBuilder();
        int bytes = 0;
        for (int codePoint : tag.codePoints().toArray()) {
            String character = Character.toString(codePoint);
            bytes += character.getBytes(StandardCharsets.UTF_8).length;
            if (bytes > MAX_COLLAPSE_ID_BYTES) {
                break;
            }
            kept.append(character);
        }
        return kept.toString();
    }

    // Anything but APNs' JSON, such as a proxy's HTML page, is reported as no reason.
    private String reasonOf(String body) {
        try {
            ApnsError error = body.isBlank() ? null : objectMapper.readValue(body, ApnsError.class);
            return error == null || error.reason() == null ? "" : error.reason();
        } catch (RuntimeException unreadable) {
            return "";
        }
    }

    private static ApnsTokenProvider tokensOf(PushProperties.Apns apns, ObjectMapper objectMapper, Clock clock) {
        if (!apns.configured()) {
            log.warn("No APNs key configured: native iOS push is off, iOS devices get no notification");
            return null;
        }
        try {
            return new ApnsTokenProvider(apns.teamId(), apns.keyId(), apns.privateKey(), objectMapper, clock);
        } catch (IllegalArgumentException invalid) {
            log.error("The APNs private key is malformed: native iOS push is off ({})", invalid.getMessage());
            return null;
        }
    }
}
