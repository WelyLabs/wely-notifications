package com.calendar.notifications.domain.models;

import java.util.List;

/**
 * Who takes part in an event, as last announced by wely-events: a local projection, since a date
 * change does not carry the member list.
 *
 * @param version grows with each change; an older one never replaces a newer one
 */
public record EventMembership(String eventId, String title, List<String> memberIds, long version) {

    public EventMembership {
        memberIds = memberIds == null ? List.of() : List.copyOf(memberIds);
    }

    /** Every member but the given user, typically whoever made the change. */
    public List<String> recipientsExcept(String userId) {
        return memberIds.stream().filter(member -> !member.equals(userId)).distinct().toList();
    }
}
