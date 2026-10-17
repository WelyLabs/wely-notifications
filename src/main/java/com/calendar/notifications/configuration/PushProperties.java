package com.calendar.notifications.configuration;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * Push settings. Without a VAPID key pair Web Push is off, without an APNs key native iOS push is off,
 * and the notification center works alone.
 *
 * @param timeout how long one push service may take to answer
 */
@ConfigurationProperties(prefix = "app.push")
public record PushProperties(Vapid vapid, Apns apns, Duration timeout) {

    static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(10);
    static final String DEFAULT_BUNDLE_ID = "com.welylabs.wely";

    public PushProperties {
        vapid = vapid == null ? new Vapid("", "", "") : vapid;
        apns = apns == null ? new Apns("", "", "", DEFAULT_BUNDLE_ID) : apns;
        timeout = timeout == null ? DEFAULT_TIMEOUT : timeout;
    }

    /**
     * @param publicKey  base64url uncompressed P-256 point
     * @param privateKey base64url 32-byte scalar
     * @param subject    a contact for the push services, such as {@code mailto:admin@example.com}
     */
    public record Vapid(String publicKey, String privateKey, String subject) {

        public boolean configured() {
            return notBlank(publicKey) && notBlank(privateKey) && notBlank(subject);
        }
    }

    /**
     * @param teamId     the Apple Developer team, the token's issuer
     * @param keyId      the id of the APNs auth key, ten characters
     * @param privateKey the .p8 file's content, a PKCS#8 PEM, armor and newlines optional
     * @param bundleId   the iOS app, sent as apns-topic
     */
    public record Apns(String teamId, String keyId, String privateKey, String bundleId) {

        public boolean configured() {
            return notBlank(teamId) && notBlank(keyId) && notBlank(privateKey) && notBlank(bundleId);
        }

        // The key is a secret: never in a log or an actuator dump.
        @Override
        public String toString() {
            return "Apns[teamId=" + teamId + ", keyId=" + keyId + ", privateKey=" + (notBlank(privateKey) ? "***" : "")
                    + ", bundleId=" + bundleId + "]";
        }
    }

    private static boolean notBlank(String value) {
        return value != null && !value.isBlank();
    }
}
