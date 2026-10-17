package com.calendar.notifications.infrastructure.push.mappers;

import com.calendar.notifications.domain.models.NotificationType;
import com.calendar.notifications.domain.models.PushMessage;
import com.calendar.notifications.infrastructure.push.models.ApnsPayload;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Named;

@Mapper(componentModel = "spring")
public interface ApnsPayloadMapper {

    String DEFAULT_SOUND = "default";
    String TIME_SENSITIVE = "time-sensitive";
    String ACTIVE = "active";

    @Mapping(target = "aps", source = ".")
    ApnsPayload toPayload(PushMessage message);

    @Mapping(target = "alert", source = ".")
    @Mapping(target = "sound", constant = DEFAULT_SOUND)
    @Mapping(target = "threadId", source = "tag")
    @Mapping(target = "interruptionLevel", source = "type", qualifiedByName = "interruptionLevel")
    ApnsPayload.Aps toAps(PushMessage message);

    ApnsPayload.Alert toAlert(PushMessage message);

    /** A call breaks through Focus; anything else is a regular notification. */
    @Named("interruptionLevel")
    default String interruptionLevel(NotificationType type) {
        return type != null && type.isCall() ? TIME_SENSITIVE : ACTIVE;
    }
}
