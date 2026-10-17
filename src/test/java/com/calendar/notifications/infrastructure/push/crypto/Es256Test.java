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
import java.util.Arrays;
import java.util.Base64;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class Es256Test {

    private final ObjectMapper objectMapper = JsonMapper.builder().build();
    private final KeyPair keys = P256.generate();

    @Test
    @DisplayName("a JWT carries the header and claims as given, signed by the key")
    void jwt_shouldEncodeHeaderAndClaimsAndSignThem() throws Exception {
        String token = Es256.jwt(Map.of("alg", "ES256"), Map.of("iss", "TEAM"), (ECPrivateKey) keys.getPrivate(),
                objectMapper);
        String[] parts = token.split("\\.");

        JsonNode header = objectMapper.readTree(Base64.getUrlDecoder().decode(parts[0]));
        JsonNode claims = objectMapper.readTree(Base64.getUrlDecoder().decode(parts[1]));
        assertThat(header.get("alg").asString()).isEqualTo("ES256");
        assertThat(claims.get("iss").asString()).isEqualTo("TEAM");
        assertThat(token).doesNotContain("=");
        assertThat(verifies(parts[0] + "." + parts[1], Base64.getUrlDecoder().decode(parts[2]))).isTrue();
    }

    @Test
    @DisplayName("the signature is raw R || S, 64 bytes, and verifies with the public key")
    void sign_shouldGiveA64ByteJoseSignature() throws Exception {
        byte[] signature = Es256.sign("payload", (ECPrivateKey) keys.getPrivate());

        assertThat(signature).hasSize(64);
        assertThat(verifies("payload", signature)).isTrue();
        assertThat(verifies("tampered", signature)).isFalse();
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

        byte[] jose = Es256.derToJose(der);

        assertThat(jose).hasSize(64);
        assertThat(Arrays.copyOfRange(jose, 0, 32)).isEqualTo(Arrays.copyOfRange(r, 1, 33));
        assertThat(jose[32]).isZero();
        assertThat(jose[63]).isEqualTo((byte) 2);
    }

    @Test
    @DisplayName("bytes that are not a DER signature are refused")
    void derToJose_shouldRejectAnythingButDer() {
        assertThatThrownBy(() -> Es256.derToJose(new byte[]{1, 2, 3, 4, 5, 6, 7, 8}))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> Es256.derToJose(new byte[]{0x30, 6, 5, 1, 1, 2, 1, 1}))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private boolean verifies(String input, byte[] signature) throws Exception {
        Signature verifier = Signature.getInstance("SHA256withECDSAinP1363Format");
        verifier.initVerify(keys.getPublic());
        verifier.update(input.getBytes(StandardCharsets.US_ASCII));
        return verifier.verify(signature);
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
