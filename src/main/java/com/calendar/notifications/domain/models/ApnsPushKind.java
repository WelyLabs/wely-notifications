package com.calendar.notifications.domain.models;

/** Which APNs token an iOS device registered: the one for notifications, or PushKit's for incoming calls. */
public enum ApnsPushKind {
    ALERT,
    VOIP
}
