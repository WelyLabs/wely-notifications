package com.calendar.notifications.infrastructure.messaging.mappers;

import com.calendar.notifications.domain.models.CallMedia;
import com.calendar.notifications.domain.models.IncomingCall;
import com.calendar.notifications.infrastructure.messaging.models.CallEventBroadcastDTO;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Named;

@Mapper(componentModel = "spring")
public interface IncomingCallMapper {

    @Mapping(target = "callId", source = "id")
    @Mapping(target = "callerId", source = "startedBy")
    @Mapping(target = "media", source = "media", qualifiedByName = "media")
    IncomingCall toIncomingCall(CallEventBroadcastDTO.CallDTO call);

    /** Anything but VIDEO rings as audio, as the notification's wording does. */
    @Named("media")
    default CallMedia media(String media) {
        return CallMedia.VIDEO.name().equals(media) ? CallMedia.VIDEO : CallMedia.AUDIO;
    }
}
