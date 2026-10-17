package com.calendar.notifications.infrastructure.push.models;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.Map;

/**
 * The JSON PushKit hands the app for an incoming call. No alert in aps: the app reports the call to
 * CallKit straight from these fields, before it can reach any API.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record VoipRingPayload(Map<String, Object> aps, String callId, String caller, String callerId, String media,
                              String conversationId, String link) {}
