package com.calendar.notifications.infrastructure.persistence.mappers;

import com.calendar.notifications.domain.models.EventMembership;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class EventMembersPersistenceMapperTest {

    private final EventMembersPersistenceMapper mapper = Mappers.getMapper(EventMembersPersistenceMapper.class);

    @Test
    @DisplayName("a membership round-trips through its entity, keyed by event")
    void toEntityAndBack_shouldKeepMembersAndVersion() {
        EventMembership membership = new EventMembership("e1", "Bowling", List.of("a", "b"), 7);

        assertThat(mapper.toEntity(membership).eventId()).isEqualTo("e1");
        assertThat(mapper.toDomain(mapper.toEntity(membership))).isEqualTo(membership);
        assertThat(mapper.toEntity(null)).isNull();
        assertThat(mapper.toDomain(null)).isNull();
    }
}
