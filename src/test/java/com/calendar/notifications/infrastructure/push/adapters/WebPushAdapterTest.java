package com.calendar.notifications.infrastructure.push.adapters;

import com.calendar.notifications.configuration.PushProperties;
import com.calendar.notifications.domain.models.Notification;
import com.calendar.notifications.domain.models.NotificationType;
import com.calendar.notifications.domain.models.PushMessage;
import com.calendar.notifications.domain.models.PushResult;
import com.calendar.notifications.domain.models.PushSubscription;
import com.calendar.notifications.infrastructure.push.crypto.P256;
import com.calendar.notifications.infrastructure.push.mappers.PushPayloadMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mapstruct.factory.Mappers;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.mock.http.client.reactive.MockClientHttpRequest;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.ExchangeStrategies;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientRequestException;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import javax.crypto.Cipher;
import javax.crypto.KeyAgreement;
import javax.crypto.Mac;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URI;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.interfaces.ECPrivateKey;
import java.security.interfaces.ECPublicKey;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.List;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;

/** Requests go to a stub exchange: what is checked is what a push service would receive. */
class WebPushAdapterTest {

    private static final Base64.Encoder B64 = Base64.getUrlEncoder().withoutPadding();
    private static final String ENDPOINT = "https://fcm.googleapis.com/fcm/send/device-1";
    // RFC 8291 Appendix A's browser: its private key reads what the adapter sends.
    private static final String UA_PUBLIC =
            "BCVxsr7N_eNgVRqvHtD0zTZsEc6-VV-JvLexhqUzORcxaOzi6-AYWXvTBHm4bjyPjs7Vd8pZGH6SRpkNtoIAiw4";
    private static final String UA_PRIVATE = "q1dXpw3UpT5VOmu_cf_v6ih07Aems3njxI-JWgLcM94";
    private static final String AUTH = "BTBZMqHH6r4Tts7J_aSIgg";
    private static final Instant NOW = Instant.parse("2026-10-15T10:00:00Z");

    private final ObjectMapper objectMapper = JsonMapper.builder().build();
    private final List<MockClientHttpRequest> sent = new ArrayList<>();
    private final KeyPair vapid = P256.generate();
    private final String vapidPublic = B64.encodeToString(P256.encode((ECPublicKey) vapid.getPublic()));
    private Supplier<Mono<ClientResponse>> answer;
    private WebClient client;

    @BeforeEach
    void setUp() {
        answer = () -> Mono.just(ClientResponse.create(HttpStatus.CREATED).build());
        client = WebClient.builder()
                .exchangeFunction(request -> {
                    MockClientHttpRequest written = new MockClientHttpRequest(request.method(), request.url());
                    written.getHeaders().putAll(request.headers());
                    sent.add(written);
                    return request.writeTo(written, ExchangeStrategies.withDefaults()).then(Mono.defer(answer));
                })
                .build();
    }

    private WebPushAdapter adapter(PushProperties.Vapid keys, Duration timeout) {
        return new WebPushAdapter(new PushProperties(keys, timeout), client, Mappers.getMapper(PushPayloadMapper.class),
                objectMapper, Clock.systemUTC());
    }

    private WebPushAdapter adapter() {
        return adapter(new PushProperties.Vapid(vapidPublic, B64.encodeToString(scalar(vapid)), "mailto:a@wely.test"),
                Duration.ofSeconds(5));
    }

    private static PushSubscription device(String p256dh) {
        return new PushSubscription("bob", ENDPOINT, p256dh, AUTH, null, NOW);
    }

    private static PushMessage ringing() {
        return PushMessage.of(new Notification("n1", "bob", NotificationType.CALL_INCOMING, "Appel vidéo",
                "On t'appelle sur Wely", "/call/k1", "k1", 1, "lea", NOW, null));
    }

    private static PushMessage friendRequest() {
        return PushMessage.of(new Notification("0d9f6c8e-7a44-4b4e-9b0b-2f7f4c1d3e21", "bob",
                NotificationType.FRIEND_REQUEST, "Demande d'ami", "Léa veut être ton ami", "/friends/lea", null, 1,
                "lea", NOW, null));
    }

    @Test
    @DisplayName("a push is POSTed to the endpoint, VAPID-signed, aes128gcm-encrypted, with its TTL, urgency and topic")
    void send_shouldPostAnEncryptedSignedPush() throws Exception {
        StepVerifier.create(adapter().send(device(UA_PUBLIC), ringing()))
                .expectNext(PushResult.DELIVERED)
                .verifyComplete();

        MockClientHttpRequest request = sent.getFirst();
        assertThat(request.getMethod()).isEqualTo(HttpMethod.POST);
        assertThat(request.getURI()).isEqualTo(URI.create(ENDPOINT));
        assertThat(request.getHeaders().getFirst("Authorization")).startsWith("vapid t=").endsWith(", k=" + vapidPublic);
        assertThat(request.getHeaders().getFirst("Content-Encoding")).isEqualTo("aes128gcm");
        assertThat(request.getHeaders().getContentType()).isEqualTo(MediaType.APPLICATION_OCTET_STREAM);
        assertThat(request.getHeaders().getFirst("TTL")).isEqualTo("30");
        assertThat(request.getHeaders().getFirst("Urgency")).isEqualTo("high");
        assertThat(request.getHeaders().getFirst("Topic")).isEqualTo("call-k1");

        JsonNode payload = objectMapper.readTree(decryptAsBrowser(bodyOf(request)));
        assertThat(payload.get("title").asString()).isEqualTo("Appel vidéo");
        assertThat(payload.get("body").asString()).isEqualTo("On t'appelle sur Wely");
        assertThat(payload.get("tag").asString()).isEqualTo("call-k1");
        assertThat(payload.get("link").asString()).isEqualTo("/call/k1");
        assertThat(payload.get("type").asString()).isEqualTo("CALL_INCOMING");
        assertThat(payload.get("requireInteraction").asBoolean()).isTrue();
    }

    @Test
    @DisplayName("a push without a topic sends no Topic header, at normal urgency for a day")
    void send_shouldOmitTheTopic_whenTheMessageHasNone() {
        StepVerifier.create(adapter().send(device(UA_PUBLIC), friendRequest()))
                .expectNext(PushResult.DELIVERED)
                .verifyComplete();

        MockClientHttpRequest request = sent.getFirst();
        assertThat(request.getHeaders().containsHeader("Topic")).isFalse();
        assertThat(request.getHeaders().getFirst("Urgency")).isEqualTo("normal");
        assertThat(request.getHeaders().getFirst("TTL")).isEqualTo("86400");
    }

    @ParameterizedTest
    @ValueSource(ints = {404, 410})
    @DisplayName("404 and 410 mean the subscription is gone")
    void send_shouldReportGone_whenTheSubscriptionExpired(int status) {
        answer = () -> Mono.just(ClientResponse.create(HttpStatus.valueOf(status)).build());

        StepVerifier.create(adapter().send(device(UA_PUBLIC), ringing()))
                .expectNext(PushResult.GONE)
                .verifyComplete();
    }

    @ParameterizedTest
    @ValueSource(ints = {400, 413, 429, 500})
    @DisplayName("any other refusal is a failure, the subscription kept")
    void send_shouldReportFailed_whenThePushServiceRefuses(int status) {
        answer = () -> Mono.just(ClientResponse.create(HttpStatus.valueOf(status)).build());

        StepVerifier.create(adapter().send(device(UA_PUBLIC), ringing()))
                .expectNext(PushResult.FAILED)
                .verifyComplete();
    }

    @Test
    @DisplayName("an unreachable push service is a failure, not an error")
    void send_shouldReportFailed_whenThePushServiceIsUnreachable() {
        answer = () -> Mono.error(new WebClientRequestException(new IOException("refused"), HttpMethod.POST,
                URI.create(ENDPOINT), new HttpHeaders()));

        StepVerifier.create(adapter().send(device(UA_PUBLIC), ringing()))
                .expectNext(PushResult.FAILED)
                .verifyComplete();
    }

    @Test
    @DisplayName("a push service that does not answer in time is a failure")
    void send_shouldReportFailed_whenThePushServiceTimesOut() {
        answer = Mono::never;
        WebPushAdapter slow = adapter(new PushProperties.Vapid(vapidPublic, B64.encodeToString(scalar(vapid)),
                "mailto:a@wely.test"), Duration.ofMillis(100));

        StepVerifier.create(slow.send(device(UA_PUBLIC), ringing()))
                .expectNext(PushResult.FAILED)
                .verifyComplete();
    }

    @Test
    @DisplayName("a browser key that is not a P-256 point fails before anything is sent")
    void send_shouldReportFailed_whenTheBrowserKeyIsInvalid() {
        byte[] offCurve = Base64.getUrlDecoder().decode(UA_PUBLIC);
        offCurve[64] ^= 1;

        StepVerifier.create(adapter().send(device(B64.encodeToString(offCurve)), ringing()))
                .expectNext(PushResult.FAILED)
                .verifyComplete();
        assertThat(sent).isEmpty();
    }

    @Test
    @DisplayName("with a key pair, push is on and the public key is served")
    void enabled_shouldHold_whenTheKeyPairIsConfigured() {
        WebPushAdapter adapter = adapter();

        assertThat(adapter.enabled()).isTrue();
        assertThat(adapter.publicKey()).isEqualTo(vapidPublic);
    }

    @Test
    @DisplayName("without a key pair, push is off, the public key empty, and nothing is sent")
    void enabled_shouldBeFalse_whenNoKeyPairIsConfigured() {
        WebPushAdapter adapter = adapter(new PushProperties.Vapid("", "", ""), Duration.ofSeconds(5));

        assertThat(adapter.enabled()).isFalse();
        assertThat(adapter.publicKey()).isEmpty();
        StepVerifier.create(adapter.send(device(UA_PUBLIC), ringing())).expectNext(PushResult.FAILED).verifyComplete();
        assertThat(sent).isEmpty();
    }

    @Test
    @DisplayName("a malformed key pair turns push off instead of failing the start")
    void publicKey_shouldBeEmpty_whenTheKeyPairIsMalformed() {
        WebPushAdapter adapter = adapter(new PushProperties.Vapid("not-a-key", "nope", "mailto:a@wely.test"),
                Duration.ofSeconds(5));

        assertThat(adapter.enabled()).isFalse();
        assertThat(adapter.publicKey()).isEmpty();
    }

    @Test
    @DisplayName("a refusal is a failure, its reason read rather than left in the connection")
    void send_shouldReadTheReason_whenThePushServiceRefuses() {
        answer = () -> Mono.just(ClientResponse.create(HttpStatus.BAD_REQUEST)
                .header("Content-Type", "application/json").body("{\"reason\":\"BadWebPushTopic\"}").build());

        StepVerifier.create(adapter().send(device(UA_PUBLIC), ringing())).expectNext(PushResult.FAILED).verifyComplete();
    }

    @Test
    @DisplayName("success codes are deliveries")
    void resultOf_shouldTreatEvery2xxAsDelivered() {
        assertThat(WebPushAdapter.resultOf(HttpStatus.OK)).isEqualTo(PushResult.DELIVERED);
        assertThat(WebPushAdapter.resultOf(HttpStatus.CREATED)).isEqualTo(PushResult.DELIVERED);
    }

    private static byte[] bodyOf(MockClientHttpRequest request) {
        return DataBufferUtils.join(request.getBody()).map(buffer -> {
            byte[] bytes = new byte[buffer.readableByteCount()];
            buffer.read(bytes);
            DataBufferUtils.release(buffer);
            return bytes;
        }).block(Duration.ofSeconds(5));
    }

    private static byte[] scalar(KeyPair pair) {
        byte[] raw = ((ECPrivateKey) pair.getPrivate()).getS().toByteArray();
        byte[] scalar = new byte[32];
        int length = Math.min(raw.length, 32);
        System.arraycopy(raw, raw.length - length, scalar, 32 - length, length);
        return scalar;
    }

    /** RFC 8291 from the browser's side, independent of the adapter's code. */
    private static byte[] decryptAsBrowser(byte[] message) throws Exception {
        ByteBuffer in = ByteBuffer.wrap(message);
        byte[] salt = new byte[16];
        in.get(salt);
        in.getInt();
        byte[] serverKey = new byte[in.get()];
        in.get(serverKey);
        byte[] ciphertext = new byte[in.remaining()];
        in.get(ciphertext);

        KeyAgreement agreement = KeyAgreement.getInstance("ECDH");
        agreement.init(P256.privateKey(Base64.getUrlDecoder().decode(UA_PRIVATE)));
        agreement.doPhase(P256.publicKey(serverKey), true);
        byte[] auth = Base64.getUrlDecoder().decode(AUTH);
        byte[] ikm = hmac(hmac(auth, agreement.generateSecret()), concat("WebPush: info\0".getBytes(StandardCharsets.US_ASCII),
                Base64.getUrlDecoder().decode(UA_PUBLIC), serverKey, new byte[]{1}));
        byte[] prk = hmac(salt, ikm);
        byte[] cek = Arrays.copyOf(hmac(prk, concat("Content-Encoding: aes128gcm\0".getBytes(StandardCharsets.US_ASCII),
                new byte[]{1})), 16);
        byte[] nonce = Arrays.copyOf(hmac(prk, concat("Content-Encoding: nonce\0".getBytes(StandardCharsets.US_ASCII),
                new byte[]{1})), 12);

        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(cek, "AES"), new GCMParameterSpec(128, nonce));
        byte[] padded = cipher.doFinal(ciphertext);
        return Arrays.copyOf(padded, padded.length - 1);
    }

    private static byte[] hmac(byte[] key, byte[] data) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(key, "HmacSHA256"));
        return mac.doFinal(data);
    }

    private static byte[] concat(byte[]... parts) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        for (byte[] part : parts) {
            out.writeBytes(part);
        }
        return out.toByteArray();
    }
}
