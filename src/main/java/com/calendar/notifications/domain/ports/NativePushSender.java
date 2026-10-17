package com.calendar.notifications.domain.ports;

import com.calendar.notifications.domain.models.ApnsDevice;
import com.calendar.notifications.domain.models.PushMessage;
import com.calendar.notifications.domain.models.PushResult;
import com.calendar.notifications.domain.models.VoipPush;
import reactor.core.publisher.Mono;

/** Sends a push to one iOS device through APNs. */
public interface NativePushSender {

    /** False while no APNs key is configured: nothing is sent. */
    boolean enabled();

    /** Never errors: a failure is reported as {@link PushResult#FAILED}. */
    Mono<PushResult> send(ApnsDevice device, PushMessage message);

    /** A PushKit push to a VOIP device; never errors either. */
    Mono<PushResult> sendVoip(ApnsDevice device, VoipPush push);
}
