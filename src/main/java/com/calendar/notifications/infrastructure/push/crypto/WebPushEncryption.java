package com.calendar.notifications.infrastructure.push.crypto;

import javax.crypto.Cipher;
import javax.crypto.KeyAgreement;
import javax.crypto.Mac;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.KeyPair;
import java.security.SecureRandom;
import java.security.interfaces.ECPublicKey;
import java.util.Arrays;

/**
 * RFC 8291 message encryption: the payload is readable only by the browser that subscribed, not by
 * the push service carrying it. One aes128gcm record (RFC 8188), keyed by an ECDH exchange between a
 * fresh key pair and the browser's key, mixed with the browser's auth secret through HKDF-SHA256.
 */
public class WebPushEncryption {

    static final int RECORD_SIZE = 4096;
    static final int SALT_LENGTH = 16;
    private static final int TAG_LENGTH = 16;
    private static final int KEY_LENGTH = 16;
    private static final int NONCE_LENGTH = 12;
    private static final byte LAST_RECORD = 0x02;
    private static final byte[] KEY_INFO = ascii("WebPush: info\0");
    private static final byte[] CEK_INFO = ascii("Content-Encoding: aes128gcm\0");
    private static final byte[] NONCE_INFO = ascii("Content-Encoding: nonce\0");

    private final SecureRandom random;

    public WebPushEncryption(SecureRandom random) {
        this.random = random;
    }

    /** The encrypted body for a push, with its aes128gcm header. */
    public byte[] encrypt(byte[] plaintext, byte[] userAgentPublicKey, byte[] authSecret) {
        byte[] salt = new byte[SALT_LENGTH];
        random.nextBytes(salt);
        return encrypt(plaintext, userAgentPublicKey, authSecret, salt, P256.generate());
    }

    /** Package-private so the RFC 8291 test vector can fix the salt and the server's key pair. */
    byte[] encrypt(byte[] plaintext, byte[] userAgentPublicKey, byte[] authSecret, byte[] salt, KeyPair ephemeral) {
        if (plaintext.length > RECORD_SIZE - TAG_LENGTH - 1) {
            throw new IllegalArgumentException("Push payload too large for a single record");
        }
        try {
            byte[] serverPublicKey = P256.encode((ECPublicKey) ephemeral.getPublic());
            byte[] sharedSecret = agree(ephemeral, userAgentPublicKey);

            byte[] keyPrk = hmac(authSecret, sharedSecret);
            byte[] ikm = expand(keyPrk, concat(KEY_INFO, userAgentPublicKey, serverPublicKey), 32);
            byte[] prk = hmac(salt, ikm);
            byte[] contentKey = expand(prk, CEK_INFO, KEY_LENGTH);
            byte[] nonce = expand(prk, NONCE_INFO, NONCE_LENGTH);

            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(contentKey, "AES"),
                    new GCMParameterSpec(TAG_LENGTH * 8, nonce));
            byte[] ciphertext = cipher.doFinal(concat(plaintext, new byte[]{LAST_RECORD}));

            return concat(header(salt, serverPublicKey), ciphertext);
        } catch (GeneralSecurityException failure) {
            throw new IllegalStateException("Web Push encryption failed", failure);
        }
    }

    /** salt (16) || record size (4, big-endian) || key id length (1) || key id: the server's public key. */
    private static byte[] header(byte[] salt, byte[] serverPublicKey) {
        return ByteBuffer.allocate(SALT_LENGTH + 4 + 1 + serverPublicKey.length)
                .put(salt)
                .putInt(RECORD_SIZE)
                .put((byte) serverPublicKey.length)
                .put(serverPublicKey)
                .array();
    }

    private static byte[] agree(KeyPair ephemeral, byte[] userAgentPublicKey) throws GeneralSecurityException {
        KeyAgreement agreement = KeyAgreement.getInstance("ECDH");
        agreement.init(ephemeral.getPrivate());
        agreement.doPhase(P256.publicKey(userAgentPublicKey), true);
        return agreement.generateSecret();
    }

    // HKDF-Expand for one block (length <= 32): HMAC(prk, info || 0x01), truncated.
    private static byte[] expand(byte[] prk, byte[] info, int length) throws GeneralSecurityException {
        return Arrays.copyOf(hmac(prk, concat(info, new byte[]{1})), length);
    }

    static byte[] hmac(byte[] key, byte[] data) throws GeneralSecurityException {
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

    private static byte[] ascii(String value) {
        return value.getBytes(StandardCharsets.US_ASCII);
    }
}
