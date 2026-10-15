package com.calendar.notifications.application.dtos;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** A browser's PushSubscription, as {@code subscription.toJSON()} gives it, with an optional label. */
public record PushSubscriptionRequest(
        @NotBlank @Size(max = 1024) String endpoint,
        @NotNull @Valid Keys keys,
        @Size(max = 100) String label
) {

    public record Keys(@NotBlank String p256dh, @NotBlank String auth) {}
}
