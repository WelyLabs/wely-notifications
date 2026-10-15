package com.calendar.notifications.infrastructure.push.crypto;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.Signature;
import java.security.interfaces.ECPrivateKey;
import java.security.interfaces.ECPublicKey;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class VapidSignerTest {

    private static final Instant NOW = Instant.parse("2026-10-15T10:00:00Z");
    private static final String ENDPOINT = "https://fcm.googleapis.com/fcm/send/abc:def";
    private static final Base64.Encoder B64 = Base64.getUrlEncoder().withoutPadding();

    private final ObjectMapper objectMapper = JsonMapper.builder().build();
    private final KeyPair keys = P256.generate();
    private final String publicKey = B64.encodeToString(P256.encode((ECPublicKey) keys.getPublic()));
    private final VapidSigner signer = new VapidSigner(publicKey, B64.encodeToString(scalar(keys)),
            "mailto:admin@wely.test", objectMapper, Clock.fixed(NOW, ZoneOffset.UTC));

    @Test
    @DisplayName("the header carries the token and the public key, in the RFC 8292 scheme")
    void authorization_shouldCarryTheTokenAndThePublicKey() {
        String header = signer.authorization(ENDPOINT);

        assertThat(header).matches("vapid t=[A-Za-z0-9_-]+\\.[A-Za-z0-9_-]+\\.[A-Za-z0-9_-]+, k=" + publicKey);
    }

    @Test
    @DisplayName("the token is an ES256 JWT for the endpoint's origin, valid twelve hours, with the contact")
    void token_shouldNameTheOriginExpiryAndSubject() {
        String[] parts = signer.authorization(ENDPOINT).substring("vapid t=".length()).split(", k=")[0].split("\\.");

        JsonNode header = objectMapper.readTree(Base64.getUrlDecoder().decode(parts[0]));
        JsonNode claims = objectMapper.readTree(Base64.getUrlDecoder().decode(parts[1]));
        assertThat(header.get("alg").asString()).isEqualTo("ES256");
        assertThat(header.get("typ").asString()).isEqualTo("JWT");
        assertThat(claims.get("aud").asString()).isEqualTo("https://fcm.googleapis.com");
        assertThat(claims.get("exp").asLong()).isEqualTo(NOW.plus(VapidSigner.VALIDITY).getEpochSecond());
        assertThat(claims.get("sub").asString()).isEqualTo("mailto:admin@wely.test");
    }

    @Test
    @DisplayName("the signature verifies with the public key, as raw R || S")
    void token_shouldBeSignedByTheKeyPair() throws Exception {
        String token = signer.token("https://push.example");
        int lastDot = token.lastIndexOf('.');
        byte[] signature = Base64.getUrlDecoder().decode(token.substring(lastDot + 1));

        Signature verifier = Signature.getInstance("SHA256withECDSAinP1363Format");
        verifier.initVerify(keys.getPublic());
        verifier.update(token.substring(0, lastDot).getBytes(StandardCharsets.US_ASCII));

        assertThat(signature).hasSize(64);
        assertThat(verifier.verify(signature)).isTrue();
    }

    @Test
    @DisplayName("DER integers lose their sign byte and short ones are left-padded")
    void derToJose_shouldStripSignBytesAndPadShortIntegers() {
        byte[] r = new byte[33];
        r[1] = (byte) 0x80;
        r[32] = 1;
        byte[] s = new byte[31];
        s[30] = 2;
        byte[] der = der(r, s);

        byte[] jose = VapidSigner.derToJose(der);

        assertThat(jose).hasSize(64);
        assertThat(Arrays.copyOfRange(jose, 0, 32)).isEqualTo(Arrays.copyOfRange(r, 1, 33));
        assertThat(jose[32]).isZero();
        assertThat(jose[63]).isEqualTo((byte) 2);
    }

    @Test
    @DisplayName("bytes that are not a DER signature are refused")
    void derToJose_shouldRejectAnythingButDer() {
        assertThatThrownBy(() -> VapidSigner.derToJose(new byte[]{1, 2, 3, 4, 5, 6, 7, 8}))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> VapidSigner.derToJose(new byte[]{0x30, 6, 5, 1, 1, 2, 1, 1}))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("the audience keeps an explicit port and drops the path")
    void origin_shouldKeepThePortAndDropThePath() {
        assertThat(VapidSigner.origin("https://push.example:8443/send/1")).isEqualTo("https://push.example:8443");
        assertThat(VapidSigner.origin("https://push.example/send/1")).isEqualTo("https://push.example");
    }

    @Test
    @DisplayName("a key that is not a P-256 key is refused at construction")
    void constructor_shouldRejectMalformedKeys() {
        Clock clock = Clock.systemUTC();
        assertThatThrownBy(() -> new VapidSigner(B64.encodeToString(new byte[65]), B64.encodeToString(scalar(keys)),
                "mailto:a@b.c", objectMapper, clock)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new VapidSigner(publicKey, B64.encodeToString(new byte[5]),
                "mailto:a@b.c", objectMapper, clock)).isInstanceOf(IllegalArgumentException.class);
    }

    private static byte[] scalar(KeyPair pair) {
        byte[] raw = ((ECPrivateKey) pair.getPrivate()).getS().toByteArray();
        byte[] scalar = new byte[32];
        int length = Math.min(raw.length, 32);
        System.arraycopy(raw, raw.length - length, scalar, 32 - length, length);
        return scalar;
    }

    private static byte[] der(byte[] r, byte[] s) {
        byte[] der = new byte[2 + 2 + r.length + 2 + s.length];
        der[0] = 0x30;
        der[1] = (byte) (der.length - 2);
        der[2] = 0x02;
        der[3] = (byte) r.length;
        System.arraycopy(r, 0, der, 4, r.length);
        der[4 + r.length] = 0x02;
        der[5 + r.length] = (byte) s.length;
        System.arraycopy(s, 0, der, 6 + r.length, s.length);
        assertThat(new BigInteger(1, r).signum()).isPositive();
        return der;
    }
}
