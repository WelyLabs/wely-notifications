package com.calendar.notifications.infrastructure.realtime.mappers;

import com.calendar.notifications.domain.models.LiveUpdate;
import com.calendar.notifications.domain.models.Notification;
import com.calendar.notifications.infrastructure.realtime.models.LiveUpdateBroadcastDTO;
import org.mapstruct.Mapper;

import java.util.Optional;

import static com.calendar.notifications.infrastructure.realtime.models.LiveUpdateBroadcastDTO.ADDED;
import static com.calendar.notifications.infrastructure.realtime.models.LiveUpdateBroadcastDTO.CLOSED;
import static com.calendar.notifications.infrastructure.realtime.models.LiveUpdateBroadcastDTO.UNREAD;

@Mapper(componentModel = "spring")
public interface LiveUpdateBroadcastMapper {

    LiveUpdateBroadcastDTO.NotificationDTO toNotificationDTO(Notification notification);

    Notification toNotification(LiveUpdateBroadcastDTO.NotificationDTO notification);

    default LiveUpdateBroadcastDTO toBroadcastDTO(String recipientId, LiveUpdate update) {
        return switch (update) {
            case LiveUpdate.NotificationAdded added ->
                    new LiveUpdateBroadcastDTO(recipientId, ADDED, toNotificationDTO(added.notification()), null);
            case LiveUpdate.UnreadChanged unread ->
                    new LiveUpdateBroadcastDTO(recipientId, UNREAD, null, unread.count());
        };
    }

    default LiveUpdateBroadcastDTO closing(String recipientId) {
        return new LiveUpdateBroadcastDTO(recipientId, CLOSED, null, null);
    }

    /** Empty for {@code CLOSED}, an unknown kind, or a record missing what its kind needs. */
    default Optional<LiveUpdate> toLiveUpdate(LiveUpdateBroadcastDTO record) {
        if (ADDED.equals(record.kind()) && record.notification() != null) {
            return Optional.of(new LiveUpdate.NotificationAdded(toNotification(record.notification())));
        }
        if (UNREAD.equals(record.kind()) && record.count() != null) {
            return Optional.of(new LiveUpdate.UnreadChanged(record.count()));
        }
        return Optional.empty();
    }
}
