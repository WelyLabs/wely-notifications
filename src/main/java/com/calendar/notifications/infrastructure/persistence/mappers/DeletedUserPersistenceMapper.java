package com.calendar.notifications.infrastructure.persistence.mappers;

import com.calendar.notifications.domain.models.DeletedUserMarker;
import com.calendar.notifications.infrastructure.persistence.entities.DeletedUserEntity;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface DeletedUserPersistenceMapper {

    DeletedUserEntity toEntity(DeletedUserMarker marker);

    DeletedUserMarker toDomain(DeletedUserEntity entity);
}
