package com.calendar.notifications.domain.models;

/** A PushKit push: an incoming call to ring through CallKit, or the order to stop ringing it. */
public sealed interface VoipPush permits VoipPush.Ring, VoipPush.Hangup {

    /** As for a ringing call's alert: useless after half a minute. */
    int TTL_SECONDS = PushMessage.CALL_TTL_SECONDS;

    String callId();

    /** @param caller what the notification calls the caller, shown by CallKit until the app knows better */
    record Ring(String callId, String caller, String callerId, CallMedia media, String conversationId, String link)
            implements VoipPush {

        public static Ring of(Notification notification, IncomingCall call) {
            return new Ring(call.callId(), notification.title(), call.callerId(), call.media(), call.conversationId(),
                    notification.link());
        }
    }

    record Hangup(String callId) implements VoipPush {}
}
