package com.calendar.notifications.infrastructure.messaging.notices;

import com.calendar.notifications.domain.models.Notification;
import com.calendar.notifications.domain.models.NotificationType;
import com.calendar.notifications.infrastructure.messaging.models.CallEventBroadcastDTO;
import com.calendar.notifications.infrastructure.messaging.models.EventDateFixedDTO;
import com.calendar.notifications.infrastructure.messaging.models.FriendshipChangedDTO;
import com.calendar.notifications.infrastructure.messaging.models.MessageBroadcastDTO;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.ZoneId;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class NotificationDraftsTest {

    private final NotificationDrafts drafts = new NotificationDrafts(new DateNoticeFormatter(ZoneId.of("Europe/Paris")));

    private static CallEventBroadcastDTO call(String type, String recipient, String status, String media) {
        return call(type, recipient, status, media, null);
    }

    private static CallEventBroadcastDTO call(String type, String recipient, String status, String media,
                                              String callerName) {
        CallEventBroadcastDTO.CallDTO call = new CallEventBroadcastDTO.CallDTO("k1", "conv1", "lea", callerName,
                media, status,
                Instant.parse("2026-10-15T10:00:00Z"), null, List.of("lea", "bob"), List.of());
        return new CallEventBroadcastDTO(type, recipient, "lea", call);
    }

    private static MessageBroadcastDTO message(String content, MessageBroadcastDTO.PhotoDTO photo, String type,
                                               String kind) {
        return new MessageBroadcastDTO("m1", "lea", "Léa", "bob", "conv1", content,
                Instant.parse("2026-10-15T10:00:00Z"), photo, type, kind);
    }

    @Test
    @DisplayName("a ringing call rings its recipient, tagged by the call, opening the call screen")
    void fromCall_shouldRingTheRecipient() {
        assertThat(drafts.fromCall(call("RINGING", "bob", "RINGING", "VIDEO"))).contains(Notification.draft("bob",
                NotificationType.CALL_INCOMING, "Appel vidéo", "On t'appelle sur Wely", "/call/k1", "k1", "lea"));
        assertThat(drafts.fromCall(call("RINGING", "bob", "RINGING", "AUDIO")).orElseThrow().title())
                .isEqualTo("Appel audio");
    }

    @Test
    @DisplayName("a ringing call with the caller's name says who calls, and the media in the body")
    void fromCall_shouldNameTheCaller_whenRinging() {
        assertThat(drafts.fromCall(call("RINGING", "bob", "RINGING", "VIDEO", "Léa"))).contains(Notification.draft(
                "bob", NotificationType.CALL_INCOMING, "Léa t'appelle", "Appel vidéo", "/call/k1", "k1", "lea"));
        assertThat(drafts.fromCall(call("RINGING", "bob", "RINGING", "AUDIO", "Léa")).orElseThrow().body())
                .isEqualTo("Appel audio");
        assertThat(drafts.fromCall(call("RINGING", "bob", "RINGING", "VIDEO", " ")).orElseThrow().title())
                .isEqualTo("Appel vidéo");
    }

    @Test
    @DisplayName("a missed call with the caller's name says whose call was missed")
    void fromCall_shouldNameTheCaller_whenMissed() {
        assertThat(drafts.fromCall(call("ENDED", "bob", "MISSED", "VIDEO", "Léa"))).contains(Notification.draft(
                "bob", NotificationType.CALL_MISSED, "Appel manqué de Léa", "Tu as manqué un appel vidéo",
                "/chat/conv1", "k1", "lea"));
        assertThat(drafts.fromCall(call("ENDED", "bob", "MISSED", "AUDIO", "Léa")).orElseThrow().body())
                .isEqualTo("Tu as manqué un appel audio");
    }

    @Test
    @DisplayName("an unanswered call becomes a missed call with the same group key, opening the conversation")
    void fromCall_shouldTellTheRecipientTheyMissedIt() {
        Notification missed = drafts.fromCall(call("ENDED", "bob", "MISSED", "AUDIO")).orElseThrow();

        assertThat(missed).isEqualTo(Notification.draft("bob", NotificationType.CALL_MISSED, "Appel audio manqué",
                "Tu as manqué un appel", "/chat/conv1", "k1", "lea"));
    }

    @Test
    @DisplayName("an answered call, the caller's own end, other events and incomplete records notify nobody")
    void fromCall_shouldIgnoreEverythingElse() {
        assertThat(drafts.fromCall(call("ENDED", "bob", "ENDED", "VIDEO"))).isEmpty();
        assertThat(drafts.fromCall(call("ENDED", "carl", "MISSED", "VIDEO"))).isPresent();
        assertThat(drafts.fromCall(new CallEventBroadcastDTO("ENDED", "lea", "bob",
                call("ENDED", "lea", "MISSED", "VIDEO").call()))).isEmpty();
        assertThat(drafts.fromCall(call("PARTICIPANT_JOINED", "bob", "ACTIVE", "VIDEO"))).isEmpty();
        assertThat(drafts.fromCall(call("RINGING", "lea", "RINGING", "VIDEO"))).isEmpty();
        assertThat(drafts.fromCall(new CallEventBroadcastDTO("RINGING", "bob", "lea", null))).isEmpty();
        assertThat(drafts.fromCall(call("RINGING", null, "RINGING", "VIDEO"))).isEmpty();
    }

    @Test
    @DisplayName("a new text message notifies its receiver, grouped by conversation, titled by its sender")
    void fromMessage_shouldNotifyTheReceiver() {
        assertThat(drafts.fromMessage(message("  Salut   toi ", null, "TEXT", "NEW"))).contains(Notification.draft(
                "bob", NotificationType.MESSAGE, "Léa", "Salut toi", "/chat/conv1", "conv1", "lea"));
    }

    @Test
    @DisplayName("records written before types and kinds travelled count as new text messages")
    void fromMessage_shouldReadMissingTypeAndKindAsNewText() {
        assertThat(drafts.fromMessage(message("Salut", null, null, null))).isPresent();
    }

    @Test
    @DisplayName("a photo reads as such, whatever the caption")
    void fromMessage_shouldSayPhotoForAPhoto() {
        MessageBroadcastDTO.PhotoDTO photo = new MessageBroadcastDTO.PhotoDTO("chat/c/m.jpg", 10, 10, false);

        assertThat(drafts.fromMessage(message("", photo, "TEXT", "NEW")).orElseThrow().body()).isEqualTo("📷 Photo");
    }

    @Test
    @DisplayName("a long message is cut to 120 characters, ellipsis included, never inside an emoji")
    void fromMessage_shouldExcerptALongMessage() {
        String body = drafts.fromMessage(message("a".repeat(300), null, "TEXT", "NEW")).orElseThrow().body();
        String emoji = drafts.fromMessage(message("a".repeat(118) + "😀" + "b".repeat(10), null, "TEXT", "NEW"))
                .orElseThrow().body();

        assertThat(body).hasSize(NotificationDrafts.EXCERPT_LENGTH).endsWith("…");
        assertThat(emoji).isEqualTo("a".repeat(118) + "…");
    }

    @Test
    @DisplayName("updates, system notices, messages to oneself or to nobody notify nobody")
    void fromMessage_shouldIgnoreEverythingElse() {
        assertThat(drafts.fromMessage(message("x", null, "TEXT", "UPDATED"))).isEmpty();
        assertThat(drafts.fromMessage(message("x", null, "SYSTEM", "NEW"))).isEmpty();
        assertThat(drafts.fromMessage(new MessageBroadcastDTO("m", "lea", "Léa", "lea", "c", "x", null, null, null, null)))
                .isEmpty();
        assertThat(drafts.fromMessage(new MessageBroadcastDTO("m", "lea", "Léa", null, "c", "x", null, null, null, null)))
                .isEmpty();
    }

    @Test
    @DisplayName("a sender without a name and a message without content still read")
    void fromMessage_shouldFallBack_whenNameOrContentIsMissing() {
        Notification draft = drafts.fromMessage(
                new MessageBroadcastDTO("m", "lea", null, "bob", "c", null, null, null, null, null)).orElseThrow();

        assertThat(draft.title()).isEqualTo("Nouveau message");
        assertThat(draft.body()).isEmpty();
    }

    @Test
    @DisplayName("a date fixed by a poll tells the member when, titled by the event")
    void fromDateFixed_shouldTellTheDate() {
        EventDateFixedDTO fixed = new EventDateFixedDTO("e1", "Bowling", Instant.parse("2026-10-15T17:00:00Z"),
                Instant.parse("2026-10-15T19:00:00Z"), "olivia", null, null);

        assertThat(drafts.fromDateFixed(fixed, "bob")).isEqualTo(Notification.draft("bob",
                NotificationType.EVENT_DATE_FIXED, "Bowling", "📅 Date fixée : jeudi 15 octobre, 19:00 – 21:00",
                "/event/e1", null, "olivia"));
    }

    @Test
    @DisplayName("a move tells the new date and the previous one")
    void fromDateFixed_shouldTellAMove() {
        EventDateFixedDTO moved = new EventDateFixedDTO("e1", null, Instant.parse("2026-10-15T18:00:00Z"), null,
                "olivia", Instant.parse("2026-10-15T17:00:00Z"), null);

        Notification draft = drafts.fromDateFixed(moved, "bob");

        assertThat(draft.type()).isEqualTo(NotificationType.EVENT_MOVED);
        assertThat(draft.title()).isEqualTo("Sortie");
        assertThat(draft.body()).isEqualTo("🔁 Sortie décalée : jeudi 15 octobre, 20:00 (au lieu de 19:00)");
    }

    @Test
    @DisplayName("a friend request tells the person asked, linking to the requester")
    void fromFriendship_shouldTellTheRequest() {
        assertThat(drafts.fromFriendship(new FriendshipChangedDTO("REQUESTED", "lea", "Léa", "bob")))
                .contains(Notification.draft("bob", NotificationType.FRIEND_REQUEST, "Demande d'ami",
                        "Léa veut être ton ami", "/friends/lea", null, "lea"));
    }

    @Test
    @DisplayName("an acceptance tells the one who had asked, linking to the new friend")
    void fromFriendship_shouldTellTheAcceptance() {
        assertThat(drafts.fromFriendship(new FriendshipChangedDTO("ACCEPTED", "bob", null, "lea")))
                .contains(Notification.draft("lea", NotificationType.FRIEND_ACCEPTED, "Nouvel ami",
                        "Quelqu'un a accepté ta demande", "/friends/bob", null, "bob"));
    }

    @Test
    @DisplayName("an unknown change or an incomplete record notifies nobody")
    void fromFriendship_shouldIgnoreEverythingElse() {
        assertThat(drafts.fromFriendship(new FriendshipChangedDTO("REMOVED", "lea", "Léa", "bob"))).isEmpty();
        assertThat(drafts.fromFriendship(new FriendshipChangedDTO(null, "lea", "Léa", "bob"))).isEmpty();
        assertThat(drafts.fromFriendship(new FriendshipChangedDTO("REQUESTED", "lea", "Léa", null))).isEmpty();
        assertThat(drafts.fromFriendship(new FriendshipChangedDTO("REQUESTED", null, "Léa", "bob"))).isEmpty();
    }
}
