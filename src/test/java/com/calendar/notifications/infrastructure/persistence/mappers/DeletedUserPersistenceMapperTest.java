package com.calendar.notifications.infrastructure.persistence.mappers;

import com.calendar.notifications.domain.models.DeletedUserMarker;
import com.calendar.notifications.infrastructure.persistence.entities.DeletedUserEntity;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class DeletedUserPersistenceMapperTest {

    private static final Instant DELETED_AT = Instant.parse("2026-10-15T10:00:00Z");
    private static final Instant ERASED_AT = Instant.parse("2026-10-15T10:00:03Z");

    private final DeletedUserPersistenceMapper mapper = Mappers.getMapper(DeletedUserPersistenceMapper.class);

    @Test
    @DisplayName("a marker is stored field for field")
    void toEntity_shouldKeepEveryField() {
        assertThat(mapper.toEntity(new DeletedUserMarker("lea", DELETED_AT, ERASED_AT)))
                .isEqualTo(new DeletedUserEntity("lea", DELETED_AT, ERASED_AT));
    }

    @Test
    @DisplayName("a stored marker reads back field for field")
    void toDomain_shouldKeepEveryField() {
        assertThat(mapper.toDomain(new DeletedUserEntity("lea", DELETED_AT, null)))
                .isEqualTo(new DeletedUserMarker("lea", DELETED_AT, null));
    }

    @Test
    @DisplayName("nothing in, nothing out")
    void toDomain_shouldMapNullToNull() {
        assertThat(mapper.toDomain(null)).isNull();
        assertThat(mapper.toEntity(null)).isNull();
    }
}
