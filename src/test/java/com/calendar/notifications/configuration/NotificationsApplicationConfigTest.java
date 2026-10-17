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
import com.calendar.notifications.domain.services.NotificationService;
import com.calendar.notifications.domain.services.PushService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

class NotificationsApplicationConfigTest {

    private final NotificationsApplicationConfig config = new NotificationsApplicationConfig();

    @Test
    @DisplayName("the clock is UTC")
    void clock_shouldBeUtc() {
        assertThat(config.clock().getZone()).isEqualTo(ZoneOffset.UTC);
    }

    @Test
    @DisplayName("the domain services are built by hand from their ports, touching none")
    void services_shouldBeBuiltFromTheirPorts() {
        SubscriptionRepository subscriptions = mock(SubscriptionRepository.class);
        PreferencesRepository preferences = mock(PreferencesRepository.class);
        PushSender sender = mock(PushSender.class);
        ApnsDeviceRepository apnsDevices = mock(ApnsDeviceRepository.class);
        NativePushSender nativeSender = mock(NativePushSender.class);
        NotificationRepository repository = mock(NotificationRepository.class);
        LiveNotifications live = mock(LiveNotifications.class);
        EventMembers eventMembers = mock(EventMembers.class);
        DeletedUserMarkers markers = mock(DeletedUserMarkers.class);
        Clock clock = Clock.systemUTC();

        PushService pushService = config.pushService(subscriptions, preferences, sender, apnsDevices, nativeSender,
                markers, clock);
        NotificationService notificationService = config.notificationService(repository, live, pushService,
                mock(NotificationWording.class), markers, clock);

        assertThat(pushService).isNotNull();
        assertThat(notificationService).isNotNull();
        assertThat(config.eventMembershipService(eventMembers)).isNotNull();
        AccountErasureService erasure = config.accountErasureService(markers, repository, subscriptions, apnsDevices,
                preferences, live, notificationService, clock);
        DeletedUsersSource source = mock(DeletedUsersSource.class);

        assertThat(erasure).isNotNull();
        assertThat(config.accountReconciliationService(source, markers, erasure, clock, Duration.ofHours(48), 500))
                .isNotNull();
        verifyNoInteractions(subscriptions, preferences, sender, apnsDevices, nativeSender, repository, live,
                eventMembers, markers, source);
    }
}
