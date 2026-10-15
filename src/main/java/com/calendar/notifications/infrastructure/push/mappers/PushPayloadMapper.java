package com.calendar.notifications.infrastructure.push.mappers;

import com.calendar.notifications.domain.models.PushMessage;
import com.calendar.notifications.infrastructure.push.models.PushPayload;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface PushPayloadMapper {

    PushPayload toPayload(PushMessage message);
}
