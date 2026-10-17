package com.calendar.notifications.domain.models;

/**
 * The facts of a ringing call a CallKit screen needs, beyond what its notification holds.
 *
 * @param callerName the caller's display name; null when wely-chat did not send one
 */
public record IncomingCall(String callId, String callerId, String callerName, CallMedia media,
                           String conversationId) {}
