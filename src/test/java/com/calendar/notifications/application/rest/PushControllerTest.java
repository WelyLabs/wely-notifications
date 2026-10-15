package com.calendar.notifications.application.rest;

import com.calendar.notifications.application.dtos.PublicKeyDTO;
import com.calendar.notifications.application.dtos.PushSubscriptionRequest;
import com.calendar.notifications.application.mappers.PushRestMapper;
import com.calendar.notifications.domain.models.PushSubscription;
import com.calendar.notifications.domain.services.PushService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mapstruct.factory.Mappers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.oauth2.jwt.Jwt;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PushControllerTest {

    @Mock
    private PushService pushService;
    @Mock
    private Jwt jwt;

    private PushController controller;

    @BeforeEach
    void setUp() {
        controller = new PushController(pushService, Mappers.getMapper(PushRestMapper.class));
    }

    @Test
    @DisplayName("the public key is served as is")
    void publicKey_shouldServeTheKey() {
        when(pushService.publicKey()).thenReturn("BPub");

        assertThat(controller.publicKey()).isEqualTo(new PublicKeyDTO("BPub"));
    }

    @Test
    @DisplayName("a device is subscribed for the token's user")
    void subscribe_shouldSubscribeForTheCaller() {
        when(jwt.getClaimAsString("businessId")).thenReturn("bob");
        PushSubscription expected = new PushSubscription(null, "https://push/1", "p", "a", null, null);
        when(pushService.subscribe("bob", expected)).thenReturn(Mono.empty());

        StepVerifier.create(controller.subscribe(jwt, new PushSubscriptionRequest("https://push/1",
                new PushSubscriptionRequest.Keys("p", "a"), null))).verifyComplete();

        verify(pushService).subscribe("bob", expected);
    }

    @Test
    @DisplayName("a device is unsubscribed as the token's user")
    void unsubscribe_shouldUnsubscribeAsTheCaller() {
        when(jwt.getClaimAsString("businessId")).thenReturn("bob");
        when(pushService.unsubscribe("bob", "https://push/1")).thenReturn(Mono.empty());

        StepVerifier.create(controller.unsubscribe(jwt, "https://push/1")).verifyComplete();

        verify(pushService).unsubscribe("bob", "https://push/1");
    }
}
