package com.calendar.notifications.infrastructure.push.crypto;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.Signature;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ApnsTokenProviderTest {

    private static final Instant NOW = Instant.parse("2026-10-15T10:00:00Z");

    private final ObjectMapper objectMapper = JsonMapper.builder().build();
    private final KeyPair keys = P256.generate();
    private final MovableClock clock = new MovableClock(NOW);

    /** What Apple's portal hands out: a PKCS#8 key in PEM armor, 64 characters a line. */
    static String p8(KeyPair pair) {
        String body = Base64.getMimeEncoder(64, "\n".getBytes(StandardCharsets.US_ASCII))
                .encodeToString(pair.getPrivate().getEncoded());
        return "-----BEGIN PRIVATE KEY-----\n" + body + "\n-----END PRIVATE KEY-----\n";
    }

    private ApnsTokenProvider provider(String pem) {
        return new ApnsTokenProvider("TEAM123456", "KEY1234567", pem, objectMapper, clock);
    }

    @Test
    @DisplayName("the token is an ES256 JWT with the key id in its header and the team and issue time as claims")
    void token_shouldNameTheKeyTeamAndIssueTime() {
        String[] parts = provider(p8(keys)).token().split("\\.");

        JsonNode header = objectMapper.readTree(Base64.getUrlDecoder().decode(parts[0]));
        JsonNode claims = objectMapper.readTree(Base64.getUrlDecoder().decode(parts[1]));
        assertThat(header.get("alg").asString()).isEqualTo("ES256");
        assertThat(header.get("kid").asString()).isEqualTo("KEY1234567");
        assertThat(header.size()).isEqualTo(2);
        assertThat(claims.get("iss").asString()).isEqualTo("TEAM123456");
        assertThat(claims.get("iat").asLong()).isEqualTo(NOW.getEpochSecond());
        assertThat(claims.size()).isEqualTo(2);
    }

    @Test
    @DisplayName("the signature verifies with the key pair's public half")
    void token_shouldBeSignedByTheP8Key() throws Exception {
        String token = provider(p8(keys)).token();
        int lastDot = token.lastIndexOf('.');

        Signature verifier = Signature.getInstance("SHA256withECDSAinP1363Format");
        verifier.initVerify(keys.getPublic());
        verifier.update(token.substring(0, lastDot).getBytes(StandardCharsets.US_ASCII));

        assertThat(verifier.verify(Base64.getUrlDecoder().decode(token.substring(lastDot + 1)))).isTrue();
    }

    @Test
    @DisplayName("the same token is reused under fifty minutes, and signed again at fifty")
    void token_shouldBeCachedFiftyMinutes() {
        ApnsTokenProvider provider = provider(p8(keys));
        String first = provider.token();

        clock.advance(ApnsTokenProvider.REFRESH_AFTER.minusSeconds(1));
        assertThat(provider.token()).isSameAs(first);

        clock.advance(Duration.ofSeconds(1));
        String second = provider.token();
        assertThat(second).isNotEqualTo(first);
        JsonNode claims = objectMapper.readTree(Base64.getUrlDecoder().decode(second.split("\\.")[1]));
        assertThat(claims.get("iat").asLong()).isEqualTo(NOW.plus(ApnsTokenProvider.REFRESH_AFTER).getEpochSecond());
    }

    @Test
    @DisplayName("the key is read with or without its armor, on one line, or with the escaped newlines of an env var")
    void constructor_shouldAcceptTheKeyInEveryShape() {
        String pem = p8(keys);
        String bare = Base64.getEncoder().encodeToString(keys.getPrivate().getEncoded());

        assertThat(provider(pem).token()).isNotBlank();
        assertThat(provider(bare).token()).isNotBlank();
        assertThat(provider(pem.replace("\n", "")).token()).isNotBlank();
        assertThat(provider(pem.replace("\n", "\\n")).token()).isNotBlank();
        assertThat(provider(pem.replace("\n", "\r\n")).token()).isNotBlank();
    }

    @Test
    @DisplayName("a missing key, a non-base64 one, a non-EC one or one off P-256 is refused at construction")
    void constructor_shouldRejectAnythingButAP256Key() throws Exception {
        KeyPairGenerator rsa = KeyPairGenerator.getInstance("RSA");
        rsa.initialize(2048);
        KeyPairGenerator p384 = KeyPairGenerator.getInstance("EC");
        p384.initialize(384);

        assertThatThrownBy(() -> provider(null)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> provider("not a key %%%")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> provider(p8(rsa.generateKeyPair()))).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> provider(p8(p384.generateKeyPair()))).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("the PEM body is the DER key, whatever surrounds it")
    void pemBody_shouldStripArmorAndWhitespace() {
        byte[] der = keys.getPrivate().getEncoded();

        assertThat(ApnsTokenProvider.pemBody(p8(keys))).isEqualTo(der);
        assertThat(ApnsTokenProvider.pemBody("  " + Base64.getEncoder().encodeToString(der) + "\n")).isEqualTo(der);
    }

    /** A clock the test moves forward by hand. */
    private static final class MovableClock extends Clock {

        private Instant now;

        MovableClock(Instant start) {
            this.now = start;
        }

        void advance(Duration by) {
            now = now.plus(by);
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    }
}
