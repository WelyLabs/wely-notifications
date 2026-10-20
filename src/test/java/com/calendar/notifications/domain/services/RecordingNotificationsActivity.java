package com.calendar.notifications.domain.services;

import com.calendar.notifications.domain.models.PushChannel;
import com.calendar.notifications.domain.ports.NotificationsActivity;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/** Test double for the activity port: records what the domain told it, in order, as plain strings. */
class RecordingNotificationsActivity implements NotificationsActivity {

    final List<String> events = new CopyOnWriteArrayList<>();

    @Override
    public void pushSent(PushChannel channel) {
        events.add("sent:" + channel);
    }

    @Override
    public void pushFailed(PushChannel channel) {
        events.add("failed:" + channel);
    }

    @Override
    public void pushExpired(PushChannel channel) {
        events.add("expired:" + channel);
    }

    @Override
    public void streamOpened() {
        events.add("streamOpened");
    }

    @Override
    public void streamClosed() {
        events.add("streamClosed");
    }
}
