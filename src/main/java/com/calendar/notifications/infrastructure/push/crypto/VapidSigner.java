package com.calendar.notifications.infrastructure.push.crypto;

import tools.jackson.databind.ObjectMapper;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.Signature;
import java.security.interfaces.ECPrivateKey;
import java.time.Clock;
import java.time.Duration;
import java.util.Arrays;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * RFC 8292 VAPID: proves to a push service which application server sends, with a short-lived JWT
 * signed ES256 by the key browsers subscribed with.
 */
public class VapidSigner {

    static final Duration VALIDITY = Duration.ofHours(12);
    private static final Base64.Encoder BASE64URL = Base64.getUrlEncoder().withoutPadding();
    private static final int COORDINATE_LENGTH = 32;
    private static final int SEQUENCE = 0x30;
    private static final int INTEGER = 0x02;
    private static final int LONG_FORM_ONE_BYTE = 0x81;

    private final String publicKey;
    private final ECPrivateKey privateKey;
    private final String subject;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    /**
     * @param publicKey  base64url uncompressed point, as browsers receive it
     * @param privateKey base64url 32-byte scalar
     * @param subject    a contact for the push service, {@code mailto:} or {@code https:}
     */
    public VapidSigner(String publicKey, String privateKey, String subject, ObjectMapper objectMapper, Clock clock) {
        P256.publicKey(Base64.getUrlDecoder().decode(publicKey));
        this.publicKey = publicKey;
        this.privateKey = P256.privateKey(Base64.getUrlDecoder().decode(privateKey));
        this.subject = subject;
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    /** The Authorization header value for a push to this endpoint. */
    public String authorization(String endpoint) {
        return "vapid t=" + token(origin(endpoint)) + ", k=" + publicKey;
    }

    /** A JWT for the push service at this origin, valid twelve hours. */
    String token(String audience) {
        Map<String, Object> header = new LinkedHashMap<>();
        header.put("typ", "JWT");
        header.put("alg", "ES256");
        Map<String, Object> claims = new LinkedHashMap<>();
        claims.put("aud", audience);
        claims.put("exp", clock.instant().plus(VALIDITY).getEpochSecond());
        claims.put("sub", subject);

        String signingInput = BASE64URL.encodeToString(objectMapper.writeValueAsBytes(header)) + "."
                + BASE64URL.encodeToString(objectMapper.writeValueAsBytes(claims));
        return signingInput + "." + BASE64URL.encodeToString(sign(signingInput));
    }

    private byte[] sign(String signingInput) {
        try {
            Signature signature = Signature.getInstance("SHA256withECDSA");
            signature.initSign(privateKey);
            signature.update(signingInput.getBytes(StandardCharsets.US_ASCII));
            return derToJose(signature.sign());
        } catch (GeneralSecurityException failure) {
            throw new IllegalStateException("VAPID signature failed", failure);
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

    /** scheme://host[:port] of the endpoint: what the push service expects as audience. */
    static String origin(String endpoint) {
        URI uri = URI.create(endpoint);
        return uri.getScheme() + "://" + uri.getHost() + (uri.getPort() == -1 ? "" : ":" + uri.getPort());
    }
}
