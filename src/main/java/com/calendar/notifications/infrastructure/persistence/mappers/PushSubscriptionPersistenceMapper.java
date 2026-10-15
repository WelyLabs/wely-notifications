package com.calendar.notifications.infrastructure.persistence.mappers;

import com.calendar.notifications.domain.models.PushSubscription;
import com.calendar.notifications.infrastructure.persistence.entities.PushSubscriptionEntity;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface PushSubscriptionPersistenceMapper {

    PushSubscriptionEntity toEntity(PushSubscription subscription);

    // The record's with-methods look like fluent setters to MapStruct; the constructor is used.
    @Mapping(target = "withUserId", ignore = true)
    @Mapping(target = "withCreatedAt", ignore = true)
    PushSubscription toDomain(PushSubscriptionEntity entity);
}
