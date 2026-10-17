package com.calendar.notifications.infrastructure.push.crypto;

import tools.jackson.databind.ObjectMapper;

import java.security.interfaces.ECPrivateKey;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * APNs provider token: a JWT signed ES256 by the team's .p8 auth key. Apple refuses one older than an
 * hour and one refreshed more often than every twenty minutes, so it is reused for fifty.
 */
public class ApnsTokenProvider {

    static final Duration REFRESH_AFTER = Duration.ofMinutes(50);
    private static final String PEM_ARMOR = "-----(BEGIN|END) [A-Z ]*PRIVATE KEY-----";

    private final String teamId;
    private final String keyId;
    private final ECPrivateKey privateKey;
    private final ObjectMapper objectMapper;
    private final Clock clock;
    private String token;
    private Instant issuedAt;

    /** @param privateKeyPem the .p8 content, PKCS#8, with or without its PEM armor and newlines */
    public ApnsTokenProvider(String teamId, String keyId, String privateKeyPem, ObjectMapper objectMapper,
                             Clock clock) {
        this.teamId = teamId;
        this.keyId = keyId;
        this.privateKey = P256.pkcs8PrivateKey(pemBody(privateKeyPem));
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    /** The current token, signed again once it is fifty minutes old. */
    public synchronized String token() {
        Instant now = clock.instant();
        if (token == null || !now.isBefore(issuedAt.plus(REFRESH_AFTER))) {
            issuedAt = now;
            token = sign(now);
        }
        return token;
    }

    private String sign(Instant at) {
        Map<String, Object> header = new LinkedHashMap<>();
        header.put("alg", "ES256");
        header.put("kid", keyId);
        Map<String, Object> claims = new LinkedHashMap<>();
        claims.put("iss", teamId);
        claims.put("iat", at.getEpochSecond());
        return Es256.jwt(header, claims, privateKey, objectMapper);
    }

    /** The DER bytes of a PEM key; the armor, newlines and escaped "\n" of an env var are all dropped. */
    static byte[] pemBody(String pem) {
        if (pem == null) {
            throw new IllegalArgumentException("No APNs private key");
        }
        String base64 = pem.replaceAll(PEM_ARMOR, "").replace("\\n", "").replaceAll("\\s", "");
        try {
            return Base64.getDecoder().decode(base64);
        } catch (IllegalArgumentException malformed) {
            throw new IllegalArgumentException("The APNs private key is not base64", malformed);
        }
    }
}
