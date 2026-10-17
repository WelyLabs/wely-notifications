package com.calendar.notifications.domain.models;

import java.time.Instant;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * One iOS device that receives native push through APNs for a user.
 *
 * @param token       the device token APNs gave the app, hex, stored lowercase
 * @param environment the APNs server that issued the token
 * @param kind        ALERT for notifications, VOIP for PushKit's incoming calls; ALERT when unset
 */
public record ApnsDevice(String userId, String token, ApnsEnvironment environment, ApnsPushKind kind,
                         Instant createdAt) {

    // 32 bytes today; Apple warns tokens may grow, so only a floor and a sanity ceiling.
    private static final Pattern HEX_TOKEN = Pattern.compile("[0-9a-f]{64,200}");

    public ApnsDevice {
        token = token == null ? null : token.toLowerCase(Locale.ROOT);
        kind = kind == null ? ApnsPushKind.ALERT : kind;
    }

    public boolean isVoip() {
        return kind == ApnsPushKind.VOIP;
    }

    /** A hex token of 64 to 200 characters and a known environment: anything else APNs would refuse. */
    public boolean isWellFormed() {
        return token != null && HEX_TOKEN.matcher(token).matches() && environment != null;
    }

    public ApnsDevice withUserId(String newUserId) {
        return new ApnsDevice(newUserId, token, environment, kind, createdAt);
    }

    public ApnsDevice withCreatedAt(Instant at) {
        return new ApnsDevice(userId, token, environment, kind, at);
    }
}
