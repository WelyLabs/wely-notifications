package com.calendar.notifications.infrastructure.persistence.mappers;

import com.calendar.notifications.domain.models.EventMembership;
import com.calendar.notifications.infrastructure.persistence.entities.EventMembersEntity;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface EventMembersPersistenceMapper {

    EventMembersEntity toEntity(EventMembership membership);

    EventMembership toDomain(EventMembersEntity entity);
}
