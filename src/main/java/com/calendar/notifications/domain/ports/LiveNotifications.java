package com.calendar.notifications.domain.ports;

import com.calendar.notifications.domain.models.LiveUpdate;
import reactor.core.publisher.Flux;

/** Fire-and-forget updates for the clients connected right now. */
public interface LiveNotifications {

    void publish(String recipientId, LiveUpdate update);

    Flux<LiveUpdate> stream(String recipientId);

    /** Ends every open stream of the recipient; a later stream starts a new channel. */
    void close(String recipientId);
}
