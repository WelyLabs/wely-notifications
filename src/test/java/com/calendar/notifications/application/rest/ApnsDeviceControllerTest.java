package com.calendar.notifications.application.rest;

import com.calendar.notifications.application.dtos.ApnsDeviceRequest;
import com.calendar.notifications.application.mappers.ApnsDeviceRestMapper;
import com.calendar.notifications.domain.models.ApnsDevice;
import com.calendar.notifications.domain.models.ApnsEnvironment;
import com.calendar.notifications.domain.models.ApnsPushKind;
import com.calendar.notifications.domain.services.PushService;
import com.calendar.notifications.exception.BusinessErrorCode;
import com.calendar.notifications.exception.BusinessException;
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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ApnsDeviceControllerTest {

    private static final String TOKEN = "ab".repeat(32);

    @Mock
    private PushService pushService;
    @Mock
    private Jwt jwt;

    private ApnsDeviceController controller;

    @BeforeEach
    void setUp() {
        controller = new ApnsDeviceController(pushService, Mappers.getMapper(ApnsDeviceRestMapper.class));
    }

    @Test
    @DisplayName("an iOS device is registered for the token's user")
    void register_shouldRegisterForTheCaller() {
        when(jwt.getClaimAsString("businessId")).thenReturn("bob");
        ApnsDevice expected = new ApnsDevice(null, TOKEN, ApnsEnvironment.PRODUCTION, ApnsPushKind.ALERT, null);
        when(pushService.registerDevice("bob", expected)).thenReturn(Mono.empty());

        StepVerifier.create(controller.register(jwt, new ApnsDeviceRequest(TOKEN, "PRODUCTION", null))).verifyComplete();

        verify(pushService).registerDevice("bob", expected);
    }

    @Test
    @DisplayName("a deleted account's refusal reaches the advice unchanged")
    void register_shouldPropagateTheRefusal_whenTheAccountIsErased() {
        when(jwt.getClaimAsString("businessId")).thenReturn("bob");
        when(pushService.registerDevice(eq("bob"), any()))
                .thenReturn(Mono.error(new BusinessException(BusinessErrorCode.ACCOUNT_DELETED)));

        StepVerifier.create(controller.register(jwt, new ApnsDeviceRequest(TOKEN, "SANDBOX", "VOIP")))
                .expectErrorSatisfies(error -> assertThat(((BusinessException) error).getErrorCode())
                        .isEqualTo(BusinessErrorCode.ACCOUNT_DELETED))
                .verify();
    }

    @Test
    @DisplayName("an iOS device is unregistered as the token's user")
    void unregister_shouldUnregisterAsTheCaller() {
        when(jwt.getClaimAsString("businessId")).thenReturn("bob");
        when(pushService.unregisterDevice("bob", TOKEN)).thenReturn(Mono.empty());

        StepVerifier.create(controller.unregister(jwt, TOKEN)).verifyComplete();

        verify(pushService).unregisterDevice("bob", TOKEN);
    }
}
