package com.calendar.notifications.domain.ports;

import com.calendar.notifications.domain.models.LiveUpdate;
import reactor.core.publisher.Flux;

/** Updates for the clients connected right now. Fire and forget: nobody connected, nothing kept. */
public interface LiveNotifications {

    void publish(String recipientId, LiveUpdate update);

    Flux<LiveUpdate> stream(String recipientId);
}
