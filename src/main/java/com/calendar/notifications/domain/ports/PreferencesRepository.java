package com.calendar.notifications.domain.ports;

import com.calendar.notifications.domain.models.NotificationPreferences;
import reactor.core.publisher.Mono;

public interface PreferencesRepository {

    /** Empty when the user never changed them. */
    Mono<NotificationPreferences> find(String userId);

    Mono<NotificationPreferences> save(NotificationPreferences preferences);

    /** Forgets the user's choices; nothing saved is not an error. */
    Mono<Void> delete(String userId);
}
