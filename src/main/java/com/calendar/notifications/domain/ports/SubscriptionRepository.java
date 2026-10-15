package com.calendar.notifications.domain.ports;

import com.calendar.notifications.domain.models.PushSubscription;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/** Push subscriptions, one per endpoint. */
public interface SubscriptionRepository {

    /** Creates or replaces the subscription of this endpoint, whoever held it before. */
    Mono<Void> save(PushSubscription subscription);

    Flux<PushSubscription> findByUser(String userId);

    /** Removes the endpoint only if it belongs to the user. */
    Mono<Void> deleteOwned(String userId, String endpoint);

    /** Removes the endpoint whoever holds it: the push service said it no longer exists. */
    Mono<Void> delete(String endpoint);

    /** Removes every device of the user. */
    Mono<Void> deleteByUser(String userId);
}
