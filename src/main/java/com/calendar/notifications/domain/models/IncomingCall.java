package com.calendar.notifications.domain.models;

/** The facts of a ringing call a CallKit screen needs, beyond what its notification holds. */
public record IncomingCall(String callId, String callerId, CallMedia media, String conversationId) {}
