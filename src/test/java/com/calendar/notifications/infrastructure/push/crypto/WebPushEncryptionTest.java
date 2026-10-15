package com.calendar.notifications.infrastructure.push.crypto;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import javax.crypto.Cipher;
import javax.crypto.KeyAgreement;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.SecureRandom;
import java.security.interfaces.ECPrivateKey;
import java.util.Arrays;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class WebPushEncryptionTest {

    // RFC 8291, Appendix A.
    private static final String PLAINTEXT = "When I grow up, I want to be a watermelon";
    private static final String AS_PUBLIC =
            "BP4z9KsN6nGRTbVYI_c7VJSPQTBtkgcy27mlmlMoZIIgDll6e3vCYLocInmYWAmS6TlzAC8wEqKK6PBru3jl7A8";
    private static final String AS_PRIVATE = "yfWPiYE-n46HLnH0KqZOF1fJJU3MYrct3AELtAQ-oRw";
    private static final String UA_PUBLIC =
            "BCVxsr7N_eNgVRqvHtD0zTZsEc6-VV-JvLexhqUzORcxaOzi6-AYWXvTBHm4bjyPjs7Vd8pZGH6SRpkNtoIAiw4";
    private static final String UA_PRIVATE = "q1dXpw3UpT5VOmu_cf_v6ih07Aems3njxI-JWgLcM94";
    private static final String AUTH_SECRET = "BTBZMqHH6r4Tts7J_aSIgg";
    private static final String SALT = "DGv6ra1nlYgDCS1FRnbzlw";
    private static final String EXPECTED =
            "DGv6ra1nlYgDCS1FRnbzlwAAEABBBP4z9KsN6nGRTbVYI_c7VJSPQTBtkgcy27mlmlMoZIIgDll6e3vCYLocInmYWAmS6TlzAC8wEqKK6PBru3jl7A_yl95bQpu6cVPTpK4Mqgkf1CXztLVBSt2Ks3oZwbuwXPXLWyouBWLVWGNWQexSgSxsj_Qulcy4a-fN";

    private final WebPushEncryption encryption = new WebPushEncryption(new SecureRandom());

    @Test
    @DisplayName("the RFC 8291 test vector comes out byte for byte")
    void encrypt_shouldReproduceTheRfc8291TestVector() {
        KeyPair server = new KeyPair(P256.publicKey(decode(AS_PUBLIC)), P256.privateKey(decode(AS_PRIVATE)));

        byte[] message = encryption.encrypt(PLAINTEXT.getBytes(StandardCharsets.UTF_8), decode(UA_PUBLIC),
                decode(AUTH_SECRET), decode(SALT), server);

        assertThat(Base64.getUrlEncoder().withoutPadding().encodeToString(message)).isEqualTo(EXPECTED);
    }

    @Test
    @DisplayName("with a fresh salt and key, the browser holding the private key reads the payload back")
    void encrypt_shouldBeReadableByTheUserAgent() throws Exception {
        byte[] message = encryption.encrypt(PLAINTEXT.getBytes(StandardCharsets.UTF_8), decode(UA_PUBLIC),
                decode(AUTH_SECRET));

        assertThat(new String(decryptAsUserAgent(message), StandardCharsets.UTF_8)).isEqualTo(PLAINTEXT);
    }

    @Test
    @DisplayName("two pushes of the same payload share neither salt nor key")
    void encrypt_shouldUseAFreshSaltAndKeyEachTime() {
        byte[] first = encryption.encrypt(new byte[]{1}, decode(UA_PUBLIC), decode(AUTH_SECRET));
        byte[] second = encryption.encrypt(new byte[]{1}, decode(UA_PUBLIC), decode(AUTH_SECRET));

        assertThat(Arrays.copyOf(first, 16)).isNotEqualTo(Arrays.copyOf(second, 16));
        assertThat(Arrays.copyOfRange(first, 21, 86)).isNotEqualTo(Arrays.copyOfRange(second, 21, 86));
    }

    @Test
    @DisplayName("a payload too large for one 4096-byte record is refused")
    void encrypt_shouldRejectAPayloadLargerThanOneRecord() {
        byte[] tooLarge = new byte[WebPushEncryption.RECORD_SIZE];

        assertThatThrownBy(() -> encryption.encrypt(tooLarge, decode(UA_PUBLIC), decode(AUTH_SECRET)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("a browser key that is not a P-256 point is refused")
    void encrypt_shouldRejectAMalformedBrowserKey() {
        assertThatThrownBy(() -> encryption.encrypt(new byte[]{1}, new byte[65], decode(AUTH_SECRET)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    /** The receiving side of RFC 8291, written independently of the class under test. */
    private static byte[] decryptAsUserAgent(byte[] message) throws Exception {
        ByteBuffer header = ByteBuffer.wrap(message);
        byte[] salt = new byte[16];
        header.get(salt);
        assertThat(header.getInt()).isEqualTo(4096);
        byte[] serverKey = new byte[header.get()];
        header.get(serverKey);
        byte[] ciphertext = new byte[header.remaining()];
        header.get(ciphertext);

        ECPrivateKey uaPrivate = P256.privateKey(decode(UA_PRIVATE));
        KeyAgreement agreement = KeyAgreement.getInstance("ECDH");
        agreement.init(uaPrivate);
        agreement.doPhase(P256.publicKey(serverKey), true);
        byte[] shared = agreement.generateSecret();

        byte[] keyPrk = WebPushEncryption.hmac(decode(AUTH_SECRET), shared);
        byte[] ikm = WebPushEncryption.hmac(keyPrk,
                concat("WebPush: info\0".getBytes(StandardCharsets.US_ASCII), decode(UA_PUBLIC), serverKey, new byte[]{1}));
        byte[] prk = WebPushEncryption.hmac(salt, ikm);
        byte[] cek = Arrays.copyOf(WebPushEncryption.hmac(prk,
                concat("Content-Encoding: aes128gcm\0".getBytes(StandardCharsets.US_ASCII), new byte[]{1})), 16);
        byte[] nonce = Arrays.copyOf(WebPushEncryption.hmac(prk,
                concat("Content-Encoding: nonce\0".getBytes(StandardCharsets.US_ASCII), new byte[]{1})), 12);

        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(cek, "AES"), new GCMParameterSpec(128, nonce));
        byte[] padded = cipher.doFinal(ciphertext);
        assertThat(padded[padded.length - 1]).isEqualTo((byte) 0x02);
        return Arrays.copyOf(padded, padded.length - 1);
    }

    private static byte[] concat(byte[]... parts) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        for (byte[] part : parts) {
            out.writeBytes(part);
        }
        return out.toByteArray();
    }

    private static byte[] decode(String base64url) {
        return Base64.getUrlDecoder().decode(base64url);
    }
}
