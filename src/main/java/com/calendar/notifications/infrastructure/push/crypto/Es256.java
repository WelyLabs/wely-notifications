package com.calendar.notifications.infrastructure.push.crypto;

import tools.jackson.databind.ObjectMapper;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.Signature;
import java.security.interfaces.ECPrivateKey;
import java.util.Arrays;
import java.util.Base64;
import java.util.Map;

/** Compact JWS signed ES256 (ECDSA P-256 with SHA-256), as VAPID and APNs provider tokens both need. */
final class Es256 {

    private static final Base64.Encoder BASE64URL = Base64.getUrlEncoder().withoutPadding();
    private static final int COORDINATE_LENGTH = 32;
    private static final int SEQUENCE = 0x30;
    private static final int INTEGER = 0x02;
    private static final int LONG_FORM_ONE_BYTE = 0x81;

    private Es256() {
    }

    /** header.claims.signature, each part base64url without padding. */
    static String jwt(Map<String, Object> header, Map<String, Object> claims, ECPrivateKey key,
                      ObjectMapper objectMapper) {
        String signingInput = BASE64URL.encodeToString(objectMapper.writeValueAsBytes(header)) + "."
                + BASE64URL.encodeToString(objectMapper.writeValueAsBytes(claims));
        return signingInput + "." + BASE64URL.encodeToString(sign(signingInput, key));
    }

    /** The JOSE signature of the input: R || S, 32 bytes each. */
    static byte[] sign(String signingInput, ECPrivateKey key) {
        try {
            Signature signature = Signature.getInstance("SHA256withECDSA");
            signature.initSign(key);
            signature.update(signingInput.getBytes(StandardCharsets.US_ASCII));
            return derToJose(signature.sign());
        } catch (GeneralSecurityException failure) {
            throw new IllegalStateException("ES256 signature failed", failure);
        }
    }

    /** JWS wants R || S, 32 bytes each; the JDK signs in DER, SEQUENCE { INTEGER r, INTEGER s }. */
    static byte[] derToJose(byte[] der) {
        if (der.length < 8 || (der[0] & 0xFF) != SEQUENCE) {
            throw new IllegalArgumentException("Not a DER ECDSA signature");
        }
        int offset = (der[1] & 0xFF) == LONG_FORM_ONE_BYTE ? 3 : 2;
        byte[] jose = new byte[2 * COORDINATE_LENGTH];
        offset = copyInteger(der, offset, jose, 0);
        copyInteger(der, offset, jose, COORDINATE_LENGTH);
        return jose;
    }

    // DER integers are signed and minimal: strip a sign byte, left-pad a short one.
    private static int copyInteger(byte[] der, int offset, byte[] target, int targetOffset) {
        if ((der[offset] & 0xFF) != INTEGER) {
            throw new IllegalArgumentException("Not a DER ECDSA signature");
        }
        int length = der[offset + 1] & 0xFF;
        byte[] value = Arrays.copyOfRange(der, offset + 2, offset + 2 + length);
        int start = Math.max(0, value.length - COORDINATE_LENGTH);
        int copied = value.length - start;
        System.arraycopy(value, start, target, targetOffset + COORDINATE_LENGTH - copied, copied);
        return offset + 2 + length;
    }
}
