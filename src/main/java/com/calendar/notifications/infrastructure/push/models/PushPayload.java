package com.calendar.notifications.infrastructure.push.models;

/** The JSON the service worker reads from an incoming push, before showing it. */
public record PushPayload(String title, String body, String tag, String link, String type,
                          boolean requireInteraction) {}
