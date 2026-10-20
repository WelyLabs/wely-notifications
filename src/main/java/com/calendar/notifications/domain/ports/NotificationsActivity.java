package com.calendar.notifications.domain.ports;

import com.calendar.notifications.domain.models.PushChannel;

/**
 * What happens in this service that is worth watching: how push services answer, and how many
 * clients are listening live. Told by the domain the moment a fact is established; how it is
 * recorded (metrics today) is the adapter's business.
 */
public interface NotificationsActivity {

    /** The push service accepted the notification for the device. */
    void pushSent(PushChannel channel);

    /** The push could not be delivered: the push service refused it, or could not be reached. */
    void pushFailed(PushChannel channel);

    /** The push service answered that the device's subscription or token is gone. */
    void pushExpired(PushChannel channel);

    /** A client opened the live stream of its notifications. */
    void streamOpened();

    /** A live stream ended: the client left, the account was erased, or the stream failed. */
    void streamClosed();
}
