package com.calendar.notifications.infrastructure.messaging.notices;

import com.calendar.notifications.domain.models.Notification;
import com.calendar.notifications.domain.models.NotificationType;
import com.calendar.notifications.infrastructure.messaging.models.CallEventBroadcastDTO;
import com.calendar.notifications.infrastructure.messaging.models.EventDateFixedDTO;
import com.calendar.notifications.infrastructure.messaging.models.FriendshipChangedDTO;
import com.calendar.notifications.infrastructure.messaging.models.MessageBroadcastDTO;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * Turns the records of the other services into notifications, worded in French. Empty when a record
 * calls for none: a message update, a call event nobody needs to hear about.
 *
 * <p>Call notifications name the caller when wely-chat sends a name, and fall back to the call's
 * media otherwise: this service keeps no user directory.
 */
@Component
public class NotificationDrafts {

    static final int EXCERPT_LENGTH = 120;
    private static final String VIDEO = "VIDEO";
    private static final String TEXT = "TEXT";
    private static final String NEW = "NEW";

    private final DateNoticeFormatter dates;

    public NotificationDrafts(DateNoticeFormatter dates) {
        this.dates = dates;
    }

    /** RINGING rings the recipient; ENDED unanswered tells them they missed it, with the same group key. */
    public Optional<Notification> fromCall(CallEventBroadcastDTO event) {
        CallEventBroadcastDTO.CallDTO call = event.call();
        if (call == null || event.recipientId() == null || event.recipientId().equals(event.actorId())) {
            return Optional.empty();
        }
        boolean video = VIDEO.equals(call.media());
        String media = video ? "vidéo" : "audio";
        String name = call.startedByName() == null || call.startedByName().isBlank() ? null : call.startedByName();
        if ("RINGING".equals(event.type())) {
            String title = name != null ? name + " t'appelle" : "Appel " + media;
            String body = name != null ? "Appel " + media : "On t'appelle sur Wely";
            return Optional.of(Notification.draft(event.recipientId(), NotificationType.CALL_INCOMING, title, body,
                    "/call/" + call.id(), call.id(), event.actorId()));
        }
        // MISSED means nobody answered: everyone but the caller missed it.
        if ("ENDED".equals(event.type()) && "MISSED".equals(call.status())
                && !event.recipientId().equals(call.startedBy())) {
            String title = name != null ? "Appel manqué de " + name : "Appel " + media + " manqué";
            String body = name != null ? "Tu as manqué un appel " + media : "Tu as manqué un appel";
            return Optional.of(Notification.draft(event.recipientId(), NotificationType.CALL_MISSED, title, body,
                    "/chat/" + call.conversationId(), call.id(), call.startedBy()));
        }
        return Optional.empty();
    }

    /** New text messages only: updates are not news, system notices arrive through their own topic. */
    public Optional<Notification> fromMessage(MessageBroadcastDTO message) {
        boolean isNew = message.kind() == null || NEW.equals(message.kind());
        boolean isText = message.type() == null || TEXT.equals(message.type());
        if (!isNew || !isText || message.receiverId() == null || message.receiverId().equals(message.senderId())) {
            return Optional.empty();
        }
        String body = message.photo() != null ? "📷 Photo" : excerpt(message.content());
        String title = message.senderName() != null ? message.senderName() : "Nouveau message";
        return Optional.of(Notification.draft(message.receiverId(), NotificationType.MESSAGE, title, body,
                "/chat/" + message.conversationId(), message.conversationId(), message.senderId()));
    }

    /** A date fixed by a poll, or moved by the organizer, told to one member. */
    public Notification fromDateFixed(EventDateFixedDTO fixed, String recipientId) {
        String body = fixed.isMove()
                ? dates.formatMove(fixed.start(), fixed.end(), fixed.previousStart(), fixed.previousEnd())
                : dates.format(fixed.start(), fixed.end());
        return Notification.draft(recipientId,
                fixed.isMove() ? NotificationType.EVENT_MOVED : NotificationType.EVENT_DATE_FIXED,
                fixed.title() != null ? fixed.title() : "Sortie", body, "/event/" + fixed.eventId(), null,
                fixed.organizerId());
    }

    /** REQUESTED tells the person asked; ACCEPTED tells the one who had asked. */
    public Optional<Notification> fromFriendship(FriendshipChangedDTO change) {
        if (change.toUserId() == null || change.fromUserId() == null) {
            return Optional.empty();
        }
        String name = change.fromUserName() != null ? change.fromUserName() : "Quelqu'un";
        String link = "/friends/" + change.fromUserId();
        return switch (change.type() == null ? "" : change.type()) {
            case "REQUESTED" -> Optional.of(Notification.draft(change.toUserId(), NotificationType.FRIEND_REQUEST,
                    "Demande d'ami", name + " veut être ton ami", link, null, change.fromUserId()));
            case "ACCEPTED" -> Optional.of(Notification.draft(change.toUserId(), NotificationType.FRIEND_ACCEPTED,
                    "Nouvel ami", name + " a accepté ta demande", link, null, change.fromUserId()));
            default -> Optional.empty();
        };
    }

    private static String excerpt(String content) {
        if (content == null) {
            return "";
        }
        String flat = content.strip().replaceAll("\\s+", " ");
        if (flat.length() <= EXCERPT_LENGTH) {
            return flat;
        }
        // Never cut an emoji in half.
        int cut = EXCERPT_LENGTH - 1;
        if (Character.isHighSurrogate(flat.charAt(cut - 1))) {
            cut--;
        }
        return flat.substring(0, cut) + "…";
    }
}
