package com.calendar.notifications.infrastructure.messaging.notices;

import com.calendar.notifications.domain.models.Notification;
import com.calendar.notifications.domain.models.NotificationType;
import com.calendar.notifications.infrastructure.messaging.models.BirthdayNoticeDTO;
import com.calendar.notifications.infrastructure.messaging.models.CallEventBroadcastDTO;
import com.calendar.notifications.infrastructure.messaging.models.EventDateFixedDTO;
import com.calendar.notifications.infrastructure.messaging.models.FriendshipChangedDTO;
import com.calendar.notifications.infrastructure.messaging.models.FriendshipNoticeDTO;
import com.calendar.notifications.infrastructure.messaging.models.MessageBroadcastDTO;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.List;
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
    private static final String CLOSE = "CLOSE";
    private static final String INTIMATE = "INTIMATE";
    private static final String SOMEONE = "Quelqu'un";
    private static final List<String> CLOSENESS_ORDER = List.of("FRIEND", CLOSE, INTIMATE);

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

    /**
     * REQUESTED tells the person asked; ACCEPTED tells the one who had asked; a closeness upgrade tells the
     * one who proposed it, the actor having accepted it in the app. Anything else, a downgrade included, is ignored.
     */
    public Optional<Notification> fromFriendship(FriendshipChangedDTO change) {
        if (change.toUserId() == null || change.fromUserId() == null) {
            return Optional.empty();
        }
        String name = change.fromUserName() != null ? change.fromUserName() : SOMEONE;
        String link = "/friends/" + change.fromUserId();
        return switch (change.type() == null ? "" : change.type()) {
            case "REQUESTED" -> Optional.of(Notification.draft(change.toUserId(), NotificationType.FRIEND_REQUEST,
                    "Demande d'ami", name + " veut être ton ami", link, null, change.fromUserId()));
            case "ACCEPTED" -> Optional.of(Notification.draft(change.toUserId(), NotificationType.FRIEND_ACCEPTED,
                    "Nouvel ami", name + " a accepté ta demande", link, null, change.fromUserId()));
            case "CLOSENESS_CHANGED" -> closenessUpgrade(change, name, link);
            default -> Optional.empty();
        };
    }

    private static Optional<Notification> closenessUpgrade(FriendshipChangedDTO change, String name, String link) {
        int previous = rank(change.previousCloseness());
        int current = rank(change.closeness());
        if (previous < 0 || current <= previous) {
            return Optional.empty();
        }
        NotificationType type = NotificationType.CLOSENESS_UPGRADED;
        return Optional.of(INTIMATE.equals(change.closeness())
                ? Notification.draft(change.toUserId(), type, "💝 " + name + " et toi êtes maintenant intimes",
                        "Votre lien passe au niveau supérieur.", link, null, change.fromUserId())
                : Notification.draft(change.toUserId(), type, "🧡 " + name + " et toi êtes maintenant proches",
                        "Vous partagez désormais un peu plus.", link, null, change.fromUserId()));
    }

    // FRIEND < CLOSE < INTIMATE; -1 for a missing or unknown level (List.of rejects indexOf(null)).
    private static int rank(String closeness) {
        return closeness == null ? -1 : CLOSENESS_ORDER.indexOf(closeness);
    }

    /**
     * A reunion nudge or a friendship anniversary, worded around the friend's name. Empty without a recipient,
     * a friend or a name, for a notice about oneself, for a type it does not know, and for an anniversary under a year.
     */
    public Optional<Notification> fromFriendshipNotice(FriendshipNoticeDTO notice) {
        String name = notice.friendName();
        if (notice.recipientId() == null || notice.friendId() == null || name == null || name.isBlank()
                || notice.type() == null || notice.recipientId().equals(notice.friendId())) {
            return Optional.empty();
        }
        return switch (notice.type()) {
            case "REUNION_NUDGE" -> Optional.of(reunionNudge(notice, name));
            case "FRIENDSHIP_ANNIVERSARY" -> notice.years() == null || notice.years() < 1
                    ? Optional.empty()
                    : Optional.of(Notification.draft(notice.recipientId(), NotificationType.FRIENDSHIP_ANNIVERSARY,
                            "💛 " + notice.years() + (notice.years() > 1 ? " ans" : " an") + " d'amitié avec " + name,
                            "Revivez vos souvenirs ensemble.", "/friends/" + notice.friendId() + "/memories",
                            "friendship-" + notice.friendId() + "-" + notice.years(), notice.friendId()));
            default -> Optional.empty();
        };
    }

    // No months apart means no outing ever shared; under two months, "un moment" reads better than "1 mois".
    private static Notification reunionNudge(FriendshipNoticeDTO notice, String name) {
        Integer months = notice.monthsApart();
        String title;
        String body;
        if (months == null) {
            title = "💭 Et si tu voyais " + name + " ?";
            body = "Vous n'avez encore partagé aucune sortie sur Wely.";
        } else {
            title = "💭 Ça fait un moment, " + name;
            body = "Vous ne vous êtes pas vus depuis " + (months < 2 ? "un moment" : months + " mois")
                    + ". Et si tu proposais une sortie ?";
        }
        return Notification.draft(notice.recipientId(), NotificationType.REUNION_NUDGE, title, body,
                "/friends/" + notice.friendId() + "?propose=1", "reunion-" + notice.friendId(), notice.friendId());
    }

    /**
     * A birthday notice, worded by the recipient's closeness to the honoree. Empty without a recipient,
     * an honoree, a name or a date, for a type it does not know, and for a surprise told to its honoree.
     */
    public Optional<Notification> fromBirthday(BirthdayNoticeDTO notice) {
        String name = notice.honoreeName();
        if (notice.recipientId() == null || notice.honoreeId() == null || notice.date() == null
                || name == null || name.isBlank() || notice.type() == null) {
            return Optional.empty();
        }
        boolean toHonoree = notice.recipientId().equals(notice.honoreeId());
        if ("BIRTHDAY_TODAY".equals(notice.type())) {
            return toHonoree
                    ? Optional.of(birthday(notice, NotificationType.BIRTHDAY_TODAY, "Joyeux anniversaire, " + name + " ! 🎂",
                            "Toute la bande Wely te souhaite une magnifique journée.",
                            "/calendar?date=" + notice.date(), null))
                    : Optional.empty();
        }
        // Everything else is about the honoree, and the surprise must stay one.
        if (toHonoree) {
            return Optional.empty();
        }
        return switch (notice.type()) {
            case "FRIEND_BIRTHDAY" -> Optional.of(friendBirthday(notice, name));
            case "SURPRISE_PROPOSAL" -> surpriseProposal(notice, name);
            case "SURPRISE_CREATED" -> notice.surpriseEventId() == null
                    ? Optional.empty()
                    : Optional.of(birthday(notice, NotificationType.BIRTHDAY_SURPRISE_CREATED,
                            "🤫 " + organizer(notice) + " organise l'anniversaire surprise de " + name,
                            "Rejoins la fête, " + name + " ne voit rien.",
                            "/event/" + notice.surpriseEventId(), notice.honoreeId()));
            default -> Optional.empty();
        };
    }

    private static Notification friendBirthday(BirthdayNoticeDTO notice, String name) {
        String link = "/friends/" + notice.honoreeId();
        NotificationType type = NotificationType.FRIEND_BIRTHDAY;
        return switch (notice.closeness() == null ? "" : notice.closeness()) {
            case INTIMATE -> birthday(notice, type, "🎂 C'est le grand jour de " + name + " !",
                    "Fais-lui une journée inoubliable.", link, notice.honoreeId());
            case CLOSE -> birthday(notice, type, "🎉 " + name + " fête son anniversaire aujourd'hui",
                    "Pense à lui écrire !", link, notice.honoreeId());
            default -> birthday(notice, type, "C'est l'anniversaire de " + name,
                    "Un petit mot lui ferait plaisir.", link, notice.honoreeId());
        };
    }

    // Without a surprise yet the count of days is the point, so a notice without one is dropped.
    private static Optional<Notification> surpriseProposal(BirthdayNoticeDTO notice, String name) {
        NotificationType type = NotificationType.BIRTHDAY_SURPRISE_PROPOSAL;
        if (notice.surpriseEventId() != null) {
            return Optional.of(birthday(notice, type, "🤫 " + organizer(notice) + " prépare une surprise pour " + name,
                    "Rejoins-la, " + name + " n'en saura rien.", "/event/" + notice.surpriseEventId(),
                    notice.honoreeId()));
        }
        if (notice.daysUntil() == null || notice.daysUntil() < 0) {
            return Optional.empty();
        }
        String link = "/calendar?surpriseFor=" + notice.honoreeId() + "&date=" + notice.date();
        int days = notice.daysUntil();
        if (INTIMATE.equals(notice.closeness())) {
            return Optional.of(birthday(notice, type, "🤫 L'anniversaire de " + name + " approche",
                    capitalize(inDays(days)) + ". Organise une surprise : tout est déjà prêt, il ne reste qu'à créer.",
                    link, notice.honoreeId()));
        }
        return Optional.of(birthday(notice, type, "🎁 " + name + " fête son anniversaire " + inDays(days),
                "Lance l'anniversaire surprise en un clic.", link, notice.honoreeId()));
    }

    private static Notification birthday(BirthdayNoticeDTO notice, NotificationType type, String title, String body,
                                         String link, String actorId) {
        return Notification.draft(notice.recipientId(), type, title, body, link,
                groupKey(notice.honoreeId(), notice.date()), actorId);
    }

    static String groupKey(String honoreeId, LocalDate date) {
        return "birthday-" + honoreeId + "-" + date.getYear();
    }

    // "dans 14 jours", but "dans 1 jour", and "aujourd'hui" for a notice sent late.
    private static String inDays(int days) {
        return switch (days) {
            case 0 -> "aujourd'hui";
            case 1 -> "dans 1 jour";
            default -> "dans " + days + " jours";
        };
    }

    private static String capitalize(String text) {
        return Character.toUpperCase(text.charAt(0)) + text.substring(1);
    }

    private static String organizer(BirthdayNoticeDTO notice) {
        String name = notice.organizerName();
        return name == null || name.isBlank() ? SOMEONE : name;
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
