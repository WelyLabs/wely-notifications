package com.calendar.notifications.application.mappers;

import com.calendar.notifications.application.dtos.PublicKeyDTO;
import com.calendar.notifications.application.dtos.PushSubscriptionRequest;
import com.calendar.notifications.domain.models.PushSubscription;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface PushRestMapper {

    /** The owner and the time are the service's to set, never the client's. */
    // The record's with-methods look like fluent setters to MapStruct; the constructor is used.
    @Mapping(target = "withUserId", ignore = true)
    @Mapping(target = "withCreatedAt", ignore = true)
    @Mapping(target = "userId", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "p256dh", source = "keys.p256dh")
    @Mapping(target = "auth", source = "keys.auth")
    PushSubscription toSubscription(PushSubscriptionRequest request);

    @Mapping(target = "publicKey", source = "publicKey")
    PublicKeyDTO toPublicKey(String publicKey);
}
