package com.calendar.notifications.infrastructure.push.models;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

/** The JSON APNs delivers to the iOS app: Apple's aps dictionary, plus the route and type the app reads. */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ApnsPayload(Aps aps, String link, String type) {

    /** @param threadId groups the notifications of one call or one conversation in Notification Center */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record Aps(Alert alert, String sound, @JsonProperty("thread-id") String threadId,
                      @JsonProperty("interruption-level") String interruptionLevel) {}

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record Alert(String title, String body) {}
}
