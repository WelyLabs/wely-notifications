package com.calendar.notifications.domain.models;

import java.net.URI;
import java.time.Instant;
import java.util.Base64;

/**
 * One browser or device that receives Web Push for a user, as the browser's PushManager describes it.
 *
 * @param p256dh the browser's P-256 public key, base64url, uncompressed (65 bytes)
 * @param auth   the browser's 16-byte authentication secret, base64url
 * @param label  optional, chosen by the user to tell devices apart
 */
public record PushSubscription(String userId, String endpoint, String p256dh, String auth, String label,
                               Instant createdAt) {

    private static final int PUBLIC_KEY_LENGTH = 65;
    private static final byte UNCOMPRESSED_POINT = 0x04;
    private static final int AUTH_LENGTH = 16;

    /** An https endpoint, a P-256 point and a 16-byte secret: anything else cannot be encrypted for. */
    public boolean isWellFormed() {
        return isHttps(endpoint) && isPublicKey(p256dh) && decodedLength(auth) == AUTH_LENGTH;
    }

    public PushSubscription withUserId(String newUserId) {
        return new PushSubscription(newUserId, endpoint, p256dh, auth, label, createdAt);
    }

    public PushSubscription withCreatedAt(Instant at) {
        return new PushSubscription(userId, endpoint, p256dh, auth, label, at);
    }

    private static boolean isHttps(String endpoint) {
        try {
            URI uri = endpoint == null ? null : URI.create(endpoint);
            return uri != null && "https".equals(uri.getScheme()) && uri.getHost() != null;
        } catch (IllegalArgumentException malformed) {
            return false;
        }
    }

    private static boolean isPublicKey(String value) {
        byte[] key = decode(value);
        return key.length == PUBLIC_KEY_LENGTH && key[0] == UNCOMPRESSED_POINT;
    }

    private static int decodedLength(String value) {
        return decode(value).length;
    }

    private static byte[] decode(String value) {
        try {
            return value == null ? new byte[0] : Base64.getUrlDecoder().decode(value);
        } catch (IllegalArgumentException malformed) {
            return new byte[0];
        }
    }
}
