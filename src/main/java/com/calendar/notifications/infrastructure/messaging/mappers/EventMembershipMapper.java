package com.calendar.notifications.infrastructure.messaging.mappers;

import com.calendar.notifications.domain.models.EventMembership;
import com.calendar.notifications.infrastructure.messaging.models.EventGroupChangedDTO;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface EventMembershipMapper {

    /** A deleted event keeps its version with nobody in it, so an older record cannot bring it back. */
    @Mapping(target = "memberIds", expression = "java(Boolean.TRUE.equals(group.deleted()) ? java.util.List.of() : group.memberIds())")
    EventMembership toMembership(EventGroupChangedDTO group);
}
