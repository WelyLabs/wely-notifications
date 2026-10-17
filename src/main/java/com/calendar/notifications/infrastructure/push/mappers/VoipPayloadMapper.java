package com.calendar.notifications.infrastructure.push.mappers;

import com.calendar.notifications.domain.models.VoipPush;
import com.calendar.notifications.infrastructure.push.models.VoipHangupPayload;
import com.calendar.notifications.infrastructure.push.models.VoipRingPayload;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

/** PushKit payloads carry an empty aps: an alert there would be shown on top of CallKit. */
@Mapper(componentModel = "spring")
public interface VoipPayloadMapper {

    @Mapping(target = "aps", expression = "java(java.util.Map.of())")
    VoipRingPayload toPayload(VoipPush.Ring ring);

    @Mapping(target = "aps", expression = "java(java.util.Map.of())")
    @Mapping(target = "ended", constant = "true")
    VoipHangupPayload toPayload(VoipPush.Hangup hangup);
}
