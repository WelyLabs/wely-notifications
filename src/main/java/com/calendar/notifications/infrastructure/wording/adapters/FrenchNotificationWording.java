package com.calendar.notifications.infrastructure.wording.adapters;

import com.calendar.notifications.domain.models.Notification;
import com.calendar.notifications.domain.ports.NotificationWording;
import org.springframework.stereotype.Component;

/** Titles in French: a group of messages reads "Léa · 3 nouveaux messages", anything else as stored. */
@Component
public class FrenchNotificationWording implements NotificationWording {

    @Override
    public String title(Notification notification) {
        if (!notification.groupable() || notification.count() <= 1) {
            return notification.title();
        }
        return notification.title() + " · " + notification.count() + " nouveaux messages";
    }
}
