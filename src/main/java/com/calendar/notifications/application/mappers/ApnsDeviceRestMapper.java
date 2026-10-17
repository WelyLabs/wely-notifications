package com.calendar.notifications.application.mappers;

import com.calendar.notifications.application.dtos.ApnsDeviceRequest;
import com.calendar.notifications.domain.models.ApnsDevice;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface ApnsDeviceRestMapper {

    /** The owner and the time are the service's to set, never the client's. */
    // The record's with-methods look like fluent setters to MapStruct; the constructor is used.
    @Mapping(target = "withUserId", ignore = true)
    @Mapping(target = "withCreatedAt", ignore = true)
    @Mapping(target = "userId", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    ApnsDevice toDevice(ApnsDeviceRequest request);
}
