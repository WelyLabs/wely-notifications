package com.calendar.notifications;

import com.calendar.notifications.domain.ports.NativePushSender;
import com.calendar.notifications.domain.ports.PushSender;
import com.calendar.notifications.domain.services.NotificationService;
import com.calendar.notifications.domain.services.PushService;
import com.calendar.notifications.domain.services.AccountErasureService;
import com.calendar.notifications.domain.services.AccountReconciliationService;
import com.calendar.notifications.infrastructure.messaging.adapters.CallEventsConsumer;
import com.calendar.notifications.infrastructure.messaging.adapters.UserDeletedConsumer;
import com.calendar.notifications.infrastructure.reconciliation.DeletedUsersReconciliationLoop;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
class NotificationsApplicationTest {

    @Autowired
    private ApplicationContext context;

    @Test
    @DisplayName("the context starts with the domain services wired by hand")
    void contextLoads_withTheDomainServices() {
        assertThat(context.getBean(NotificationService.class)).isNotNull();
        assertThat(context.getBean(PushService.class)).isNotNull();
        assertThat(context.getBean(AccountErasureService.class)).isNotNull();
    }

    @Test
    @DisplayName("without VAPID keys the service starts with push off")
    void contextLoads_withPushOff_whenNoKeysAreConfigured() {
        assertThat(context.getBean(PushSender.class).enabled()).isFalse();
    }

    @Test
    @DisplayName("without an APNs key the service starts with native iOS push off")
    void contextLoads_withApnsOff_whenNoKeyIsConfigured() {
        assertThat(context.getBean(NativePushSender.class).enabled()).isFalse();
    }

    /** A consumer bean would make the binder dial a broker that is not there in the unit tests. */
    @Test
    @DisplayName("the consumers stay off when disabled")
    void contextLoads_withoutConsumers_whenDisabled() {
        assertThat(context.getBeansOfType(CallEventsConsumer.class)).isEmpty();
        assertThat(context.getBeansOfType(UserDeletedConsumer.class)).isEmpty();
    }

    /** The loop would call wely-users a minute after startup; the test profile switches it off. */
    @Test
    @DisplayName("the reconciliation loop stays off when disabled")
    void contextLoads_withoutReconciliationLoop_whenDisabled() {
        assertThat(context.getBeansOfType(DeletedUsersReconciliationLoop.class)).isEmpty();
        assertThat(context.getBean(AccountReconciliationService.class)).isNotNull();
    }
}
