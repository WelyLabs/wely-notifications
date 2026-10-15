package com.calendar.notifications.domain.models;

import java.util.EnumMap;
import java.util.Map;

/** Which categories a user receives as push; a category never set counts as on. */
public record NotificationPreferences(String userId, Map<NotificationCategory, Boolean> push) {

    public NotificationPreferences {
        push = push == null ? Map.of() : Map.copyOf(push);
    }

    /** Every category on: what a user has before touching the settings. */
    public static NotificationPreferences defaults(String userId) {
        Map<NotificationCategory, Boolean> all = new EnumMap<>(NotificationCategory.class);
        for (NotificationCategory category : NotificationCategory.values()) {
            all.put(category, true);
        }
        return new NotificationPreferences(userId, all);
    }

    public boolean allowsPush(NotificationCategory category) {
        return push.getOrDefault(category, true);
    }
}
