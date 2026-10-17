package com.calendar.notifications.infrastructure.push.adapters;

import com.calendar.notifications.configuration.PushProperties;
import com.calendar.notifications.domain.models.ApnsDevice;
import com.calendar.notifications.domain.models.ApnsEnvironment;
import com.calendar.notifications.domain.models.Notification;
import com.calendar.notifications.domain.models.NotificationType;
import com.calendar.notifications.domain.models.PushMessage;
import com.calendar.notifications.domain.models.PushResult;
import com.calendar.notifications.domain.models.PushUrgency;
import com.calendar.notifications.infrastructure.push.crypto.P256;
import com.calendar.notifications.infrastructure.push.mappers.ApnsPayloadMapper;
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

import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.Signature;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;

/** Requests go to a stub exchange, below the HTTP/2 connector: what is checked is what APNs would receive. */
class ApnsAdapterTest {

    private static final Instant NOW = Instant.parse("2026-10-15T10:00:00Z");
    private static final String TOKEN = "a1b2c3d4e5f60718293a4b5c6d7e8f90a1b2c3d4e5f60718293a4b5c6d7e8f90";
    private static final String BUNDLE_ID = "com.welylabs.wely";

    private final ObjectMapper objectMapper = JsonMapper.builder().build();
    private final List<MockClientHttpRequest> sent = new ArrayList<>();
    private final KeyPair key = P256.generate();
    private Supplier<Mono<ClientResponse>> answer;
    private WebClient client;

    @BeforeEach
    void setUp() {
        answer = () -> Mono.just(ClientResponse.create(HttpStatus.OK).build());
        client = WebClient.builder()
                .exchangeFunction(request -> {
                    MockClientHttpRequest written = new MockClientHttpRequest(request.method(), request.url());
                    written.getHeaders().putAll(request.headers());
                    sent.add(written);
                    return request.writeTo(written, ExchangeStrategies.withDefaults()).then(Mono.defer(answer));
                })
                .build();
    }

    private String p8() {
        return "-----BEGIN PRIVATE KEY-----\n"
                + Base64.getMimeEncoder(64, "\n".getBytes(StandardCharsets.US_ASCII))
                .encodeToString(key.getPrivate().getEncoded())
                + "\n-----END PRIVATE KEY-----";
    }

    private ApnsAdapter adapter(PushProperties.Apns apns, Duration timeout) {
        return new ApnsAdapter(new PushProperties(null, apns, timeout), client,
                Mappers.getMapper(ApnsPayloadMapper.class), objectMapper, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    private ApnsAdapter adapter() {
        return adapter(new PushProperties.Apns("TEAM123456", "KEY1234567", p8(), BUNDLE_ID), Duration.ofSeconds(5));
    }

    private static ApnsDevice iphone(ApnsEnvironment environment) {
        return new ApnsDevice("bob", TOKEN, environment, NOW);
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

    private Supplier<Mono<ClientResponse>> refusal(int status, String body) {
        return () -> Mono.just(ClientResponse.create(HttpStatus.valueOf(status))
                .header("Content-Type", "application/json").body(body).build());
    }

    @Test
    @DisplayName("a call is POSTed to production APNs, signed, high priority, expiring with its TTL, collapsed by its tag")
    void send_shouldPostASignedAlertToProduction() throws Exception {
        StepVerifier.create(adapter().send(iphone(ApnsEnvironment.PRODUCTION), ringing()))
                .expectNext(PushResult.DELIVERED)
                .verifyComplete();

        MockClientHttpRequest request = sent.getFirst();
        HttpHeaders headers = request.getHeaders();
        assertThat(request.getMethod()).isEqualTo(HttpMethod.POST);
        assertThat(request.getURI()).isEqualTo(URI.create("https://api.push.apple.com/3/device/" + TOKEN));
        assertThat(headers.getFirst("authorization")).startsWith("bearer ");
        assertThat(verifies(headers.getFirst("authorization").substring("bearer ".length()))).isTrue();
        assertThat(headers.getFirst("apns-topic")).isEqualTo(BUNDLE_ID);
        assertThat(headers.getFirst("apns-push-type")).isEqualTo("alert");
        assertThat(headers.getFirst("apns-priority")).isEqualTo("10");
        assertThat(headers.getFirst("apns-expiration")).isEqualTo(String.valueOf(NOW.getEpochSecond() + 30));
        assertThat(headers.getFirst("apns-collapse-id")).isEqualTo("call-k1");
        assertThat(headers.getContentType()).isEqualTo(MediaType.APPLICATION_JSON);

        JsonNode body = objectMapper.readTree(bodyOf(request));
        assertThat(body.at("/aps/alert/title").asString()).isEqualTo("Appel vidéo");
        assertThat(body.at("/aps/alert/body").asString()).isEqualTo("On t'appelle sur Wely");
        assertThat(body.at("/aps/sound").asString()).isEqualTo("default");
        assertThat(body.at("/aps/thread-id").asString()).isEqualTo("call-k1");
        assertThat(body.at("/aps/interruption-level").asString()).isEqualTo("time-sensitive");
        assertThat(body.get("link").asString()).isEqualTo("/call/k1");
        assertThat(body.get("type").asString()).isEqualTo("CALL_INCOMING");
    }

    @Test
    @DisplayName("a sandbox token goes to the sandbox server; anything but a call is priority 5, for a day, active")
    void send_shouldUseTheSandboxAndNormalPriority_forADevelopmentBuild() {
        StepVerifier.create(adapter().send(iphone(ApnsEnvironment.SANDBOX), friendRequest()))
                .expectNext(PushResult.DELIVERED)
                .verifyComplete();

        MockClientHttpRequest request = sent.getFirst();
        assertThat(request.getURI()).isEqualTo(URI.create("https://api.sandbox.push.apple.com/3/device/" + TOKEN));
        assertThat(request.getHeaders().getFirst("apns-priority")).isEqualTo("5");
        assertThat(request.getHeaders().getFirst("apns-expiration"))
                .isEqualTo(String.valueOf(NOW.getEpochSecond() + 86_400));
        assertThat(objectMapper.readTree(bodyOf(request)).at("/aps/interruption-level").asString()).isEqualTo("active");
    }

    @Test
    @DisplayName("a message without a tag goes without a collapse id or a thread")
    void send_shouldOmitTheCollapseId_whenTheMessageHasNoTag() {
        PushMessage untagged = new PushMessage("t", "b", null, "/x", NotificationType.MESSAGE, false, 60,
                PushUrgency.NORMAL);

        StepVerifier.create(adapter().send(iphone(ApnsEnvironment.PRODUCTION), untagged))
                .expectNext(PushResult.DELIVERED)
                .verifyComplete();

        MockClientHttpRequest request = sent.getFirst();
        assertThat(request.getHeaders().containsHeader("apns-collapse-id")).isFalse();
        assertThat(objectMapper.readTree(bodyOf(request)).get("aps").has("thread-id")).isFalse();
    }

    @Test
    @DisplayName("two pushes within fifty minutes share one provider token")
    void send_shouldReuseTheProviderToken() {
        ApnsAdapter adapter = adapter();

        adapter.send(iphone(ApnsEnvironment.PRODUCTION), ringing()).block(Duration.ofSeconds(5));
        adapter.send(iphone(ApnsEnvironment.PRODUCTION), friendRequest()).block(Duration.ofSeconds(5));

        assertThat(sent.get(1).getHeaders().getFirst("authorization"))
                .isEqualTo(sent.get(0).getHeaders().getFirst("authorization"));
    }

    @Test
    @DisplayName("410 means the token is gone")
    void send_shouldReportGone_on410() {
        answer = refusal(410, "{\"reason\":\"Unregistered\",\"timestamp\":1700000000000}");

        StepVerifier.create(adapter().send(iphone(ApnsEnvironment.PRODUCTION), ringing()))
                .expectNext(PushResult.GONE)
                .verifyComplete();
    }

    @ParameterizedTest
    @ValueSource(strings = {"BadDeviceToken", "Unregistered"})
    @DisplayName("400 for a bad or unregistered token means the token is gone")
    void send_shouldReportGone_whenTheTokenIsRefused(String reason) {
        answer = refusal(400, "{\"reason\":\"" + reason + "\"}");

        StepVerifier.create(adapter().send(iphone(ApnsEnvironment.PRODUCTION), ringing()))
                .expectNext(PushResult.GONE)
                .verifyComplete();
    }

    @ParameterizedTest
    @ValueSource(strings = {"{\"reason\":\"BadCollapseId\"}", "{\"reason\":\"DeviceTokenNotForTopic\"}",
            "<html>proxy</html>", "{}", ""})
    @DisplayName("400 for any other reason, or none, is a failure and the token is kept")
    void send_shouldReportFailed_whenTheRequestIsRefusedForAnotherReason(String body) {
        answer = refusal(400, body);

        StepVerifier.create(adapter().send(iphone(ApnsEnvironment.PRODUCTION), ringing()))
                .expectNext(PushResult.FAILED)
                .verifyComplete();
    }

    @ParameterizedTest
    @ValueSource(ints = {403, 404, 413, 429, 500, 503})
    @DisplayName("any other refusal is a failure, the token kept")
    void send_shouldReportFailed_whenApnsRefuses(int status) {
        answer = refusal(status, "{\"reason\":\"ExpiredProviderToken\"}");

        StepVerifier.create(adapter().send(iphone(ApnsEnvironment.PRODUCTION), ringing()))
                .expectNext(PushResult.FAILED)
                .verifyComplete();
    }

    @Test
    @DisplayName("an unreachable APNs is a failure, not an error")
    void send_shouldReportFailed_whenApnsIsUnreachable() {
        answer = () -> Mono.error(new WebClientRequestException(new IOException("refused"), HttpMethod.POST,
                URI.create("https://api.push.apple.com/3/device/x"), new HttpHeaders()));

        StepVerifier.create(adapter().send(iphone(ApnsEnvironment.PRODUCTION), ringing()))
                .expectNext(PushResult.FAILED)
                .verifyComplete();
    }

    @Test
    @DisplayName("an APNs that does not answer within app.push.timeout is a failure")
    void send_shouldReportFailed_whenApnsTimesOut() {
        answer = Mono::never;
        ApnsAdapter slow = adapter(new PushProperties.Apns("TEAM123456", "KEY1234567", p8(), BUNDLE_ID),
                Duration.ofMillis(100));

        StepVerifier.create(slow.send(iphone(ApnsEnvironment.PRODUCTION), ringing()))
                .expectNext(PushResult.FAILED)
                .verifyComplete();
    }

    @Test
    @DisplayName("with a key, APNs is on")
    void enabled_shouldHold_whenTheKeyIsConfigured() {
        assertThat(adapter().enabled()).isTrue();
    }

    @Test
    @DisplayName("without a key, APNs is off and nothing is sent")
    void enabled_shouldBeFalse_whenNoKeyIsConfigured() {
        ApnsAdapter off = adapter(new PushProperties.Apns("", "", "", BUNDLE_ID), Duration.ofSeconds(5));

        assertThat(off.enabled()).isFalse();
        StepVerifier.create(off.send(iphone(ApnsEnvironment.PRODUCTION), ringing()))
                .expectNext(PushResult.FAILED)
                .verifyComplete();
        assertThat(sent).isEmpty();
    }

    @Test
    @DisplayName("a malformed key turns APNs off instead of failing the start")
    void enabled_shouldBeFalse_whenTheKeyIsMalformed() {
        ApnsAdapter off = adapter(new PushProperties.Apns("TEAM123456", "KEY1234567", "not a key", BUNDLE_ID),
                Duration.ofSeconds(5));

        assertThat(off.enabled()).isFalse();
    }

    @Test
    @DisplayName("2xx is delivered; 410, or 400 for a bad or unregistered token, is gone; the rest failed")
    void resultOf_shouldMapStatusAndReason() {
        assertThat(ApnsAdapter.resultOf(200, "")).isEqualTo(PushResult.DELIVERED);
        assertThat(ApnsAdapter.resultOf(299, "")).isEqualTo(PushResult.DELIVERED);
        assertThat(ApnsAdapter.resultOf(410, "")).isEqualTo(PushResult.GONE);
        assertThat(ApnsAdapter.resultOf(400, "BadDeviceToken")).isEqualTo(PushResult.GONE);
        assertThat(ApnsAdapter.resultOf(400, "Unregistered")).isEqualTo(PushResult.GONE);
        assertThat(ApnsAdapter.resultOf(400, "BadTopic")).isEqualTo(PushResult.FAILED);
        assertThat(ApnsAdapter.resultOf(403, "BadDeviceToken")).isEqualTo(PushResult.FAILED);
        assertThat(ApnsAdapter.resultOf(300, "")).isEqualTo(PushResult.FAILED);
        assertThat(ApnsAdapter.resultOf(199, "")).isEqualTo(PushResult.FAILED);
    }

    @Test
    @DisplayName("the device URI picks the server of the token's environment")
    void deviceUri_shouldPickTheEnvironmentsServer() {
        assertThat(ApnsAdapter.deviceUri(iphone(ApnsEnvironment.PRODUCTION)))
                .isEqualTo(URI.create("https://api.push.apple.com/3/device/" + TOKEN));
        assertThat(ApnsAdapter.deviceUri(iphone(ApnsEnvironment.SANDBOX)))
                .isEqualTo(URI.create("https://api.sandbox.push.apple.com/3/device/" + TOKEN));
    }

    @Test
    @DisplayName("high urgency is priority 10, normal is 5")
    void priorityOf_shouldMapUrgency() {
        assertThat(ApnsAdapter.priorityOf(PushUrgency.HIGH)).isEqualTo("10");
        assertThat(ApnsAdapter.priorityOf(PushUrgency.NORMAL)).isEqualTo("5");
    }

    @Test
    @DisplayName("the collapse id is the tag cut to 64 bytes, never inside a character, and absent without a tag")
    void collapseId_shouldTruncateTo64Bytes() {
        assertThat(ApnsAdapter.collapseId("conv-c1")).isEqualTo("conv-c1");
        assertThat(ApnsAdapter.collapseId("x".repeat(70))).isEqualTo("x".repeat(64));
        assertThat(ApnsAdapter.collapseId("é".repeat(40))).isEqualTo("é".repeat(32));
        assertThat(ApnsAdapter.collapseId("a" + "é".repeat(40))).isEqualTo("a" + "é".repeat(31));
        assertThat(ApnsAdapter.collapseId(null)).isNull();
        assertThat(ApnsAdapter.collapseId(" ")).isNull();
    }

    private boolean verifies(String jwt) throws Exception {
        int lastDot = jwt.lastIndexOf('.');
        Signature verifier = Signature.getInstance("SHA256withECDSAinP1363Format");
        verifier.initVerify(key.getPublic());
        verifier.update(jwt.substring(0, lastDot).getBytes(StandardCharsets.US_ASCII));
        return verifier.verify(Base64.getUrlDecoder().decode(jwt.substring(lastDot + 1)));
    }

    private static byte[] bodyOf(MockClientHttpRequest request) {
        return DataBufferUtils.join(request.getBody()).map(buffer -> {
            byte[] bytes = new byte[buffer.readableByteCount()];
            buffer.read(bytes);
            DataBufferUtils.release(buffer);
            return bytes;
        }).block(Duration.ofSeconds(5));
    }
}
