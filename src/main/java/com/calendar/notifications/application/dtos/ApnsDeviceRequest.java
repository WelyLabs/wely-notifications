package com.calendar.notifications.application.dtos;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * An iOS device's APNs registration.
 *
 * @param token       the device token in hex, as the app formats the Data it receives
 * @param environment SANDBOX for a development build, PRODUCTION for TestFlight and the App Store
 */
public record ApnsDeviceRequest(
        @NotBlank @Size(max = 200) String token,
        @NotNull @Pattern(regexp = "SANDBOX|PRODUCTION") String environment
) {}
