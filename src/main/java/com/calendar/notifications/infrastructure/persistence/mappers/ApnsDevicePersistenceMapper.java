package com.calendar.notifications.infrastructure.persistence.mappers;

import com.calendar.notifications.domain.models.ApnsDevice;
import com.calendar.notifications.infrastructure.persistence.entities.ApnsDeviceEntity;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface ApnsDevicePersistenceMapper {

    ApnsDeviceEntity toEntity(ApnsDevice device);

    // The record's with-methods look like fluent setters to MapStruct; the constructor is used.
    @Mapping(target = "withUserId", ignore = true)
    @Mapping(target = "withCreatedAt", ignore = true)
    ApnsDevice toDomain(ApnsDeviceEntity entity);
}
