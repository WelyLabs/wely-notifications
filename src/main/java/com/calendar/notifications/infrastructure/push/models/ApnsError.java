package com.calendar.notifications.infrastructure.push.models;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/** The body APNs answers a refusal with, such as {"reason": "BadDeviceToken"}. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ApnsError(String reason) {}
