package com.calendar.notifications.configuration;

import com.calendar.notifications.domain.ports.EventMembers;
import com.calendar.notifications.domain.ports.LiveNotifications;
import com.calendar.notifications.domain.ports.NotificationRepository;
import com.calendar.notifications.domain.ports.NotificationWording;
import com.calendar.notifications.domain.ports.PreferencesRepository;
import com.calendar.notifications.domain.ports.PushSender;
import com.calendar.notifications.domain.ports.SubscriptionRepository;
import com.calendar.notifications.domain.services.PushService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Clock;
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
        NotificationRepository repository = mock(NotificationRepository.class);
        LiveNotifications live = mock(LiveNotifications.class);
        EventMembers eventMembers = mock(EventMembers.class);
        Clock clock = Clock.systemUTC();

        PushService pushService = config.pushService(subscriptions, preferences, sender, clock);

        assertThat(pushService).isNotNull();
        assertThat(config.notificationService(repository, live, pushService, mock(NotificationWording.class), clock))
                .isNotNull();
        assertThat(config.eventMembershipService(eventMembers)).isNotNull();
        verifyNoInteractions(subscriptions, preferences, sender, repository, live, eventMembers);
    }
}
