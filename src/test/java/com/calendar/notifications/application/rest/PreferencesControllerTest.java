package com.calendar.notifications.application.rest;

import com.calendar.notifications.application.dtos.PreferencesDTO;
import com.calendar.notifications.application.mappers.PreferencesRestMapper;
import com.calendar.notifications.domain.models.NotificationCategory;
import com.calendar.notifications.domain.models.NotificationPreferences;
import com.calendar.notifications.domain.services.PushService;
import com.calendar.notifications.exception.NotificationsErrorCode;
import com.calendar.notifications.exception.NotificationsException;
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

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PreferencesControllerTest {

    @Mock
    private PushService pushService;
    @Mock
    private Jwt jwt;

    private PreferencesController controller;

    @BeforeEach
    void setUp() {
        controller = new PreferencesController(pushService, Mappers.getMapper(PreferencesRestMapper.class));
        when(jwt.getClaimAsString("businessId")).thenReturn("bob");
    }

    @Test
    @DisplayName("the caller's preferences are read as five booleans")
    void get_shouldReadTheCallersPreferences() {
        when(pushService.preferences("bob")).thenReturn(Mono.just(NotificationPreferences.defaults("bob")));

        StepVerifier.create(controller.get(jwt)).expectNext(new PreferencesDTO(true, true, true, true, true)).verifyComplete();
    }

    @Test
    @DisplayName("the caller's preferences are saved and echoed")
    void update_shouldSaveAndEcho() {
        NotificationPreferences saved = new NotificationPreferences("bob", Map.of(NotificationCategory.CALLS, false,
                NotificationCategory.MESSAGES, true, NotificationCategory.EVENTS, true, NotificationCategory.FRIENDS, true, NotificationCategory.BIRTHDAYS, false));
        when(pushService.updatePreferences(eq("bob"), any())).thenReturn(Mono.just(saved));

        StepVerifier.create(controller.update(jwt, new PreferencesDTO(false, true, true, true, false)))
                .expectNext(new PreferencesDTO(false, true, true, true, false))
                .verifyComplete();
    }

    @Test
    @DisplayName("a deleted account's refusal reaches the advice unchanged")
    void update_shouldPropagateTheRefusal_whenTheAccountIsErased() {
        when(pushService.updatePreferences(eq("bob"), any()))
                .thenReturn(Mono.error(new NotificationsException(NotificationsErrorCode.ACCOUNT_DELETED)));

        StepVerifier.create(controller.update(jwt, new PreferencesDTO(true, true, true, true, true)))
                .expectErrorSatisfies(error -> assertThat(((NotificationsException) error).getErrorCode())
                        .isEqualTo(NotificationsErrorCode.ACCOUNT_DELETED))
                .verify();
    }
}
