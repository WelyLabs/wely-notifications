package com.calendar.notifications.configuration;

import com.calendar.notifications.domain.ports.EventMembers;
import com.calendar.notifications.domain.ports.LiveNotifications;
import com.calendar.notifications.domain.ports.NotificationRepository;
import com.calendar.notifications.domain.ports.NotificationWording;
import com.calendar.notifications.domain.ports.PreferencesRepository;
import com.calendar.notifications.domain.ports.PushSender;
import com.calendar.notifications.domain.ports.SubscriptionRepository;
import com.calendar.notifications.domain.services.EventMembershipService;
import com.calendar.notifications.domain.services.NotificationService;
import com.calendar.notifications.domain.services.PushService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.mongodb.repository.config.EnableReactiveMongoRepositories;

import java.time.Clock;

/** Wires the domain services by hand, so the domain carries no Spring annotation. */
@Configuration
@EnableReactiveMongoRepositories(basePackages = "com.calendar.notifications.infrastructure.persistence.repositories")
public class NotificationsApplicationConfig {

    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }

    @Bean
    public PushService pushService(SubscriptionRepository subscriptions, PreferencesRepository preferences,
                                   PushSender sender, Clock clock) {
        return new PushService(subscriptions, preferences, sender, clock);
    }

    @Bean
    public NotificationService notificationService(NotificationRepository repository, LiveNotifications live,
                                                   PushService pushService, NotificationWording wording, Clock clock) {
        return new NotificationService(repository, live, pushService, wording, clock);
    }

    @Bean
    public EventMembershipService eventMembershipService(EventMembers eventMembers) {
        return new EventMembershipService(eventMembers);
    }
}
