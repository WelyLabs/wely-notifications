package com.calendar.notifications.infrastructure.messaging.mappers;

import com.calendar.notifications.domain.models.EventMembership;
import com.calendar.notifications.infrastructure.messaging.models.EventGroupChangedDTO;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class EventMembershipMapperTest {

    private final EventMembershipMapper mapper = Mappers.getMapper(EventMembershipMapper.class);

    @Test
    @DisplayName("a live event keeps its members and version")
    void toMembership_shouldKeepMembersAndVersion() {
        EventGroupChangedDTO group = new EventGroupChangedDTO("e1", "Bowling", "olivia", List.of("olivia", "bob"), 3, false);

        assertThat(mapper.toMembership(group))
                .isEqualTo(new EventMembership("e1", "Bowling", List.of("olivia", "bob"), 3));
    }

    @Test
    @DisplayName("a deleted event has nobody left, at its version")
    void toMembership_shouldEmptyADeletedEvent() {
        EventGroupChangedDTO group = new EventGroupChangedDTO("e1", "Bowling", "olivia", List.of("olivia"), 4, true);

        assertThat(mapper.toMembership(group)).isEqualTo(new EventMembership("e1", "Bowling", List.of(), 4));
    }

    @Test
    @DisplayName("a record from before deletions existed is a live event")
    void toMembership_shouldReadAMissingDeletedFlagAsLive() {
        EventGroupChangedDTO group = new EventGroupChangedDTO("e1", "Bowling", "olivia", List.of("bob"), 2, null);

        assertThat(mapper.toMembership(group)).isEqualTo(new EventMembership("e1", "Bowling", List.of("bob"), 2));
    }

    @Test
    @DisplayName("nothing in, nothing out")
    void toMembership_shouldMapNullToNull() {
        assertThat(mapper.toMembership(null)).isNull();
    }
}
