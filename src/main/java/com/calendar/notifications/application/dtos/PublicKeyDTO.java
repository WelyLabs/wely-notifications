package com.calendar.notifications.application.dtos;

/** The VAPID public key browsers subscribe with; empty while push is not configured. */
public record PublicKeyDTO(String publicKey) {}
