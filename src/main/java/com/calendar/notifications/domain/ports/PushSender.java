package com.calendar.notifications.domain.ports;

import com.calendar.notifications.domain.models.PushMessage;
import com.calendar.notifications.domain.models.PushResult;
import com.calendar.notifications.domain.models.PushSubscription;
import reactor.core.publisher.Mono;

/** Sends a push to one device through its browser's push service. */
public interface PushSender {

    /** False while no server key is configured: nothing is sent. */
    boolean enabled();

    /** The server's public key browsers subscribe with; empty when not configured. */
    String publicKey();

    /** Never errors: a failure is reported as {@link PushResult#FAILED}. */
    Mono<PushResult> send(PushSubscription subscription, PushMessage message);
}
