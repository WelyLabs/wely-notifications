package com.calendar.notifications.infrastructure.push.models;

import java.util.Map;

/** The JSON PushKit hands the app when a call stopped ringing: the app ends its CallKit call. */
public record VoipHangupPayload(Map<String, Object> aps, String callId, boolean ended) {}
