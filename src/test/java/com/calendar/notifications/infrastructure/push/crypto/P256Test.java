package com.calendar.notifications.infrastructure.push.crypto;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.security.KeyPair;
import java.security.interfaces.ECPublicKey;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class P256Test {

    // RFC 8291 Appendix A's browser key.
    private static final String POINT =
            "BCVxsr7N_eNgVRqvHtD0zTZsEc6-VV-JvLexhqUzORcxaOzi6-AYWXvTBHm4bjyPjs7Vd8pZGH6SRpkNtoIAiw4";

    @Test
    @DisplayName("an uncompressed point reads into a key and encodes back to the same bytes")
    void publicKey_shouldRoundTripThroughEncode() {
        byte[] point = Base64.getUrlDecoder().decode(POINT);

        assertThat(P256.encode(P256.publicKey(point))).isEqualTo(point);
    }

    @Test
    @DisplayName("a point of the wrong length, compressed, or off the curve is refused")
    void publicKey_shouldRejectAnythingButAnUncompressedPoint() {
        byte[] compressed = Base64.getUrlDecoder().decode(POINT);
        compressed[0] = 0x02;
        byte[] offCurve = Base64.getUrlDecoder().decode(POINT);
        offCurve[64] ^= 1;

        assertThatThrownBy(() -> P256.publicKey(new byte[64])).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> P256.publicKey(compressed)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> P256.publicKey(offCurve)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("a 32-byte scalar reads into a private key; any other length is refused")
    void privateKey_shouldReadA32ByteScalarOnly() {
        byte[] scalar = Base64.getUrlDecoder().decode("q1dXpw3UpT5VOmu_cf_v6ih07Aems3njxI-JWgLcM94");

        assertThat(P256.privateKey(scalar).getS().signum()).isPositive();
        assertThatThrownBy(() -> P256.privateKey(new byte[31])).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("encode pads short coordinates to 32 bytes")
    void encode_shouldAlwaysGive65Bytes() {
        for (int i = 0; i < 20; i++) {
            assertThat(P256.encode((ECPublicKey) P256.generate().getPublic())).hasSize(65).startsWith((byte) 0x04);
        }
    }

    @Test
    @DisplayName("each generated pair is fresh")
    void generate_shouldGiveAFreshPairEachTime() {
        KeyPair first = P256.generate();
        KeyPair second = P256.generate();

        assertThat(P256.encode((ECPublicKey) first.getPublic()))
                .isNotEqualTo(P256.encode((ECPublicKey) second.getPublic()));
    }
}
