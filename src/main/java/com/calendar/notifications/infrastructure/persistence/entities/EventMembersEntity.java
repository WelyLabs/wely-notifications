package com.calendar.notifications.infrastructure.persistence.entities;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.util.List;

/** The projection of an event's members, versioned as wely-events numbers its changes. */
@Document(collection = "event_members")
public record EventMembersEntity(@Id String eventId, String title, List<String> memberIds, long version) {}
