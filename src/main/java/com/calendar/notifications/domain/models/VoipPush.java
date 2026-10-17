package com.calendar.notifications.domain.models;

/** A PushKit push: an incoming call to ring through CallKit, or the order to stop ringing it. */
public sealed interface VoipPush permits VoipPush.Ring, VoipPush.Hangup {

    /** As for a ringing call's alert: useless after half a minute. */
    int TTL_SECONDS = PushMessage.CALL_TTL_SECONDS;

    String callId();

    /** @param caller the caller's name, or the notification's title without one; shown by CallKit */
    record Ring(String callId, String caller, String callerId, CallMedia media, String conversationId, String link)
            implements VoipPush {

        public static Ring of(Notification notification, IncomingCall call) {
            String caller = call.callerName() == null || call.callerName().isBlank()
                    ? notification.title()
                    : call.callerName();
            return new Ring(call.callId(), caller, call.callerId(), call.media(), call.conversationId(),
                    notification.link());
        }
    }

    record Hangup(String callId) implements VoipPush {}
}
