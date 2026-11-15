package com.calendar.notifications.infrastructure.realtime.mappers;

import com.calendar.notifications.domain.models.LiveUpdate;
import com.calendar.notifications.domain.models.Notification;
import com.calendar.notifications.domain.models.NotificationType;
import com.calendar.notifications.infrastructure.realtime.models.LiveUpdateBroadcastDTO;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class LiveUpdateBroadcastMapperTest {

    private final LiveUpdateBroadcastMapper mapper = new LiveUpdateBroadcastMapperImpl();

    private static Notification notification() {
        return new Notification("n1", "bob", NotificationType.MESSAGE, "Léa", "Salut", "/chat/c1", "c1", 2, "lea",
                Instant.parse("2026-11-13T10:00:00Z"), null);
    }

    @Test
    void toBroadcastDTO_shouldCarryTheNotificationWhenAdded() {
        LiveUpdateBroadcastDTO record = mapper.toBroadcastDTO("bob", new LiveUpdate.NotificationAdded(notification()));

        assertThat(record.kind()).isEqualTo(LiveUpdateBroadcastDTO.ADDED);
        assertThat(record.recipientId()).isEqualTo("bob");
        assertThat(record.notification().type()).isEqualTo("MESSAGE");
        assertThat(record.count()).isNull();
    }

    @Test
    void toBroadcastDTO_shouldCarryTheCountWhenUnreadChanged() {
        LiveUpdateBroadcastDTO record = mapper.toBroadcastDTO("bob", new LiveUpdate.UnreadChanged(3));

        assertThat(record.kind()).isEqualTo(LiveUpdateBroadcastDTO.UNREAD);
        assertThat(record.count()).isEqualTo(3L);
        assertThat(record.notification()).isNull();
    }

    @Test
    void closing_shouldOnlyNameTheRecipient() {
        assertThat(mapper.closing("bob"))
                .isEqualTo(new LiveUpdateBroadcastDTO("bob", LiveUpdateBroadcastDTO.CLOSED, null, null));
    }

    @Test
    void toLiveUpdate_shouldRoundTripAnAddedNotification() {
        LiveUpdate update = new LiveUpdate.NotificationAdded(notification());

        assertThat(mapper.toLiveUpdate(mapper.toBroadcastDTO("bob", update))).contains(update);
    }

    @Test
    void toLiveUpdate_shouldRoundTripAnUnreadCount() {
        LiveUpdate update = new LiveUpdate.UnreadChanged(5);

        assertThat(mapper.toLiveUpdate(mapper.toBroadcastDTO("bob", update))).contains(update);
    }

    @Test
    void toLiveUpdate_shouldBeEmptyForClosedUnknownOrIncompleteRecords() {
        assertThat(mapper.toLiveUpdate(mapper.closing("bob"))).isEmpty();
        assertThat(mapper.toLiveUpdate(new LiveUpdateBroadcastDTO("bob", "RENAMED", null, null))).isEmpty();
        assertThat(mapper.toLiveUpdate(new LiveUpdateBroadcastDTO("bob", LiveUpdateBroadcastDTO.ADDED, null, null))).isEmpty();
        assertThat(mapper.toLiveUpdate(new LiveUpdateBroadcastDTO("bob", LiveUpdateBroadcastDTO.UNREAD, null, null))).isEmpty();
    }
}
