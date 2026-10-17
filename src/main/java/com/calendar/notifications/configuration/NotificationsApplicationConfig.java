package com.calendar.notifications.configuration;

import com.calendar.notifications.domain.ports.ApnsDeviceRepository;
import com.calendar.notifications.domain.ports.DeletedUserMarkers;
import com.calendar.notifications.domain.ports.DeletedUsersSource;
import com.calendar.notifications.domain.ports.EventMembers;
import com.calendar.notifications.domain.ports.LiveNotifications;
import com.calendar.notifications.domain.ports.NativePushSender;
import com.calendar.notifications.domain.ports.NotificationRepository;
import com.calendar.notifications.domain.ports.NotificationWording;
import com.calendar.notifications.domain.ports.PreferencesRepository;
import com.calendar.notifications.domain.ports.PushSender;
import com.calendar.notifications.domain.ports.SubscriptionRepository;
import com.calendar.notifications.domain.services.AccountErasureService;
import com.calendar.notifications.domain.services.AccountReconciliationService;
import com.calendar.notifications.domain.services.EventMembershipService;
import com.calendar.notifications.domain.services.NotificationService;
import com.calendar.notifications.domain.services.PushService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.mongodb.repository.config.EnableReactiveMongoRepositories;

import java.time.Clock;
import java.time.Duration;

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
                                   PushSender sender, ApnsDeviceRepository apnsDevices,
                                   NativePushSender nativeSender, DeletedUserMarkers markers, Clock clock) {
        return new PushService(subscriptions, preferences, sender, apnsDevices, nativeSender, markers, clock);
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
                                                       ApnsDeviceRepository apnsDevices,
                                                       PreferencesRepository preferences, LiveNotifications live,
                                                       NotificationService notificationService, Clock clock) {
        return new AccountErasureService(markers, notifications, subscriptions, apnsDevices, preferences, live,
                notificationService, clock);
    }

    @Bean
    public AccountReconciliationService accountReconciliationService(
            DeletedUsersSource source, DeletedUserMarkers markers, AccountErasureService accountErasureService,
            Clock clock,
            @Value("${app.account-erasure.reconciliation.recent-window:48h}") Duration recentWindow,
            @Value("${app.account-erasure.reconciliation.page-size:500}") int pageSize) {
        return new AccountReconciliationService(source, markers, accountErasureService, clock, recentWindow,
                pageSize);
    }
}
