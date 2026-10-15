package com.calendar.notifications.configuration;

import com.calendar.notifications.domain.ports.DeletedUserMarkers;
import com.calendar.notifications.domain.ports.EventMembers;
import com.calendar.notifications.domain.ports.LiveNotifications;
import com.calendar.notifications.domain.ports.NotificationRepository;
import com.calendar.notifications.domain.ports.NotificationWording;
import com.calendar.notifications.domain.ports.PreferencesRepository;
import com.calendar.notifications.domain.ports.PushSender;
import com.calendar.notifications.domain.ports.SubscriptionRepository;
import com.calendar.notifications.domain.services.AccountErasureService;
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
                                   PushSender sender, DeletedUserMarkers markers, Clock clock) {
        return new PushService(subscriptions, preferences, sender, markers, clock);
    }

    @Bean
    public NotificationService notificationService(NotificationRepository repository, LiveNotifications live,
                                                   PushService pushService, NotificationWording wording,
                                                   DeletedUserMarkers markers, Clock clock) {
        return new NotificationService(repository, live, pushService, wording, markers, clock);
    }

    @Bean
    public EventMembershipService eventMembershipService(EventMembers eventMembers) {
        return new EventMembershipService(eventMembers);
    }

    @Bean
    public AccountErasureService accountErasureService(DeletedUserMarkers markers, NotificationRepository notifications,
                                                       SubscriptionRepository subscriptions,
                                                       PreferencesRepository preferences, LiveNotifications live,
                                                       NotificationService notificationService, Clock clock) {
        return new AccountErasureService(markers, notifications, subscriptions, preferences, live,
                notificationService, clock);
    }
}
