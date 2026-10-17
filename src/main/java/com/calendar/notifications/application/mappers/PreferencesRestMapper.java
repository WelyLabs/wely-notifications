package com.calendar.notifications.application.mappers;

import com.calendar.notifications.application.dtos.PreferencesDTO;
import com.calendar.notifications.domain.models.NotificationCategory;
import com.calendar.notifications.domain.models.NotificationPreferences;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.util.EnumMap;
import java.util.Map;

/** One boolean per category on the wire, a map in the domain. */
@Mapper(componentModel = "spring", imports = NotificationCategory.class)
public interface PreferencesRestMapper {

    @Mapping(target = "calls", expression = "java(preferences.allowsPush(NotificationCategory.CALLS))")
    @Mapping(target = "messages", expression = "java(preferences.allowsPush(NotificationCategory.MESSAGES))")
    @Mapping(target = "events", expression = "java(preferences.allowsPush(NotificationCategory.EVENTS))")
    @Mapping(target = "friends", expression = "java(preferences.allowsPush(NotificationCategory.FRIENDS))")
    @Mapping(target = "birthdays", expression = "java(preferences.allowsPush(NotificationCategory.BIRTHDAYS))")
    PreferencesDTO toDto(NotificationPreferences preferences);

    /** The user is set by the service, from the token. */
    default NotificationPreferences toDomain(PreferencesDTO dto) {
        Map<NotificationCategory, Boolean> push = new EnumMap<>(NotificationCategory.class);
        push.put(NotificationCategory.CALLS, dto.calls());
        push.put(NotificationCategory.MESSAGES, dto.messages());
        push.put(NotificationCategory.EVENTS, dto.events());
        push.put(NotificationCategory.FRIENDS, dto.friends());
        // Left out, the category stays unset: on.
        if (dto.birthdays() != null) {
            push.put(NotificationCategory.BIRTHDAYS, dto.birthdays());
        }
        return new NotificationPreferences(null, push);
    }
}
