package com.calendar.notifications.infrastructure.messaging.mappers;

import com.calendar.notifications.domain.models.EventMembership;
import com.calendar.notifications.infrastructure.messaging.models.EventGroupChangedDTO;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface EventMembershipMapper {

    /** A deleted event keeps its version but no member and no title, so an older record cannot revive it. */
    @Mapping(target = "memberIds", expression = "java(Boolean.TRUE.equals(group.deleted()) ? java.util.List.of() : group.memberIds())")
    @Mapping(target = "title", expression = "java(Boolean.TRUE.equals(group.deleted()) ? null : group.title())")
    EventMembership toMembership(EventGroupChangedDTO group);
}
