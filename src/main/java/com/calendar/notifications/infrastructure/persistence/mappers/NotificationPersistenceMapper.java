package com.calendar.notifications.infrastructure.persistence.mappers;

import com.calendar.notifications.domain.models.Notification;
import com.calendar.notifications.infrastructure.persistence.entities.NotificationEntity;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface NotificationPersistenceMapper {

    /** The group stays open to merging only while unread. */
    @Mapping(target = "openGroupKey",
            expression = "java(notification.groupable() && !notification.isRead() ? notification.groupKey() : null)")
    NotificationEntity toEntity(Notification notification);

    // The record's with-methods look like fluent setters to MapStruct; the constructor is used.
    @Mapping(target = "withId", ignore = true)
    @Mapping(target = "withCreatedAt", ignore = true)
    @Mapping(target = "withTitle", ignore = true)
    @Mapping(target = "withRecipient", ignore = true)
    Notification toDomain(NotificationEntity entity);
}
