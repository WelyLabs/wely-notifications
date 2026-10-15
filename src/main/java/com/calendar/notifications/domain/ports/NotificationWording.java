package com.calendar.notifications.domain.ports;

import com.calendar.notifications.domain.models.Notification;

/** The title shown for a notification, in the users' language; a group's depends on its count. */
public interface NotificationWording {

    String title(Notification notification);
}
