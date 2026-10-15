package com.calendar.notifications.configuration;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * Web Push settings. Without a VAPID key pair, push is off and the notification center works alone.
 *
 * @param timeout how long one push service may take to answer
 */
@ConfigurationProperties(prefix = "app.push")
public record PushProperties(Vapid vapid, Duration timeout) {

    static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(10);

    public PushProperties {
        vapid = vapid == null ? new Vapid("", "", "") : vapid;
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

        private static boolean notBlank(String value) {
            return value != null && !value.isBlank();
        }
    }
}
