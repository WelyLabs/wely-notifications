package com.calendar.notifications.infrastructure.messaging.models;

import java.time.Instant;
import java.util.List;

/**
 * A record of the call events topic, as wely-chat publishes it: one call event addressed to one person.
 * Duplicated from wely-chat on purpose — no code is shared between the services, the topic is the contract.
 */
public record CallEventBroadcastDTO(String type, String recipientId, String actorId, CallDTO call) {

    public record CallDTO(String id, String conversationId, String startedBy, String media, String status,
                          Instant startedAt, Instant endedAt, List<String> memberIds,
                          List<ParticipantDTO> participants) {}

    public record ParticipantDTO(String userId, String sessionId, Instant joinedAt, Instant lastSeenAt,
                                 List<String> trackNames) {}
}
