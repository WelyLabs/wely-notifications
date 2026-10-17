package com.calendar.notifications.infrastructure.push.crypto;

import tools.jackson.databind.ObjectMapper;

import java.net.URI;
import java.security.interfaces.ECPrivateKey;
import java.time.Clock;
import java.time.Duration;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * RFC 8292 VAPID: proves to a push service which application server sends, with a short-lived JWT
 * signed ES256 by the key browsers subscribed with.
 */
public class VapidSigner {

    static final Duration VALIDITY = Duration.ofHours(12);

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
        return Es256.jwt(header, claims, privateKey, objectMapper);
    }

    /** scheme://host[:port] of the endpoint: what the push service expects as audience. */
    static String origin(String endpoint) {
        URI uri = URI.create(endpoint);
        return uri.getScheme() + "://" + uri.getHost() + (uri.getPort() == -1 ? "" : ":" + uri.getPort());
    }
}
