package com.calendar.notifications.domain.ports;

import com.calendar.notifications.domain.models.ApnsDevice;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/** iOS devices registered for APNs, one per device token. */
public interface ApnsDeviceRepository {

    /** Creates or replaces the device of this token, whoever held it before. */
    Mono<Void> save(ApnsDevice device);

    Flux<ApnsDevice> findByUser(String userId);

    /** Removes the token only if it belongs to the user. */
    Mono<Void> deleteOwned(String userId, String token);

    /** Removes the token whoever holds it: APNs said it is no longer valid. */
    Mono<Void> delete(String token);

    /** Removes every iOS device of the user. */
    Mono<Void> deleteByUser(String userId);
}
