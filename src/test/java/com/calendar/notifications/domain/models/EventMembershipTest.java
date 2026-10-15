package com.calendar.notifications.domain.models;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class EventMembershipTest {

    @Test
    @DisplayName("everyone but the given user, once each")
    void recipientsExcept_shouldLeaveOutTheUserAndDuplicates() {
        EventMembership membership = new EventMembership("e1", "Bowling", List.of("olivia", "bob", "carl", "bob"), 3);

        assertThat(membership.recipientsExcept("olivia")).containsExactly("bob", "carl");
    }

    @Test
    @DisplayName("no member list reads as nobody")
    void constructor_shouldTreatANullListAsEmpty() {
        assertThat(new EventMembership("e1", "Bowling", null, 1).recipientsExcept("olivia")).isEmpty();
    }
}
