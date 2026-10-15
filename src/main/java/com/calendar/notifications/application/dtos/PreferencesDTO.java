package com.calendar.notifications.application.dtos;

import jakarta.validation.constraints.NotNull;

/** Push on or off per category. */
public record PreferencesDTO(@NotNull Boolean calls, @NotNull Boolean messages, @NotNull Boolean events,
                             @NotNull Boolean friends) {}
