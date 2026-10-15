package com.calendar.notifications.infrastructure.messaging.models;

import java.time.Instant;

/**
 * A record of the message broadcast topic, as wely-chat publishes it: one message for one receiver.
 * Duplicated from wely-chat on purpose — no code is shared between the services, the topic is the contract.
 */
public record MessageBroadcastDTO(
        String id,
        String senderId,
        String senderName,
        String receiverId,
        String conversationId,
        String content,
        Instant timestamp,
        /** Its storage key and size; never a URL, which each pod signs when it delivers the message. */
        PhotoDTO photo,
        /** A MessageType name; absent from records written before types travelled, which are TEXT. */
        String type,
        /** NEW or UPDATED; absent from records written before updates were pushed, which are all NEW. */
        String kind
) {
    public record PhotoDTO(String key, int width, int height, boolean removed) {}
}
