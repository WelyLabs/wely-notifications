package com.calendar.notifications.application.dtos;

import jakarta.validation.constraints.NotNull;

/** Push on or off per category; {@code birthdays} may be left out by older clients, and then reads as on. */
public record PreferencesDTO(@NotNull Boolean calls, @NotNull Boolean messages, @NotNull Boolean events,
                             @NotNull Boolean friends, Boolean birthdays) {}
