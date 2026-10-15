package com.calendar.notifications.application.mappers;

import com.calendar.notifications.application.dtos.NotificationDTO;
import com.calendar.notifications.application.dtos.UnreadCountDTO;
import com.calendar.notifications.domain.models.Notification;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface NotificationRestMapper {

    @Mapping(target = "category", expression = "java(notification.category())")
    NotificationDTO toDto(Notification notification);

    @Mapping(target = "count", source = "count")
    UnreadCountDTO toUnreadCount(Long count);
}
