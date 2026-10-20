package com.calendar.notifications.application.rest;

import com.calendar.notifications.application.dtos.NotificationDTO;
import com.calendar.notifications.application.dtos.ReadRequest;
import com.calendar.notifications.application.dtos.UnreadCountDTO;
import com.calendar.notifications.application.mappers.NotificationRestMapper;
import com.calendar.notifications.domain.models.LiveUpdate;
import com.calendar.notifications.domain.models.Notification;
import com.calendar.notifications.domain.models.NotificationType;
import com.calendar.notifications.domain.services.NotificationService;
import com.calendar.notifications.exception.BusinessErrorCode;
import com.calendar.notifications.exception.BusinessException;
import com.calendar.notifications.exception.TechnicalErrorCode;
import com.calendar.notifications.exception.TechnicalException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mapstruct.factory.Mappers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.security.oauth2.jwt.Jwt;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NotificationControllerTest {

    private static final Instant AT = Instant.parse("2026-10-15T10:00:00Z");

    @Mock
    private NotificationService notificationService;
    @Mock
    private Jwt jwt;

    private NotificationController controller;

    @BeforeEach
    void setUp() {
        controller = new NotificationController(notificationService, Mappers.getMapper(NotificationRestMapper.class),
                Duration.ofMillis(50));
        when(jwt.getClaimAsString("businessId")).thenReturn("bob");
    }

    private static Notification notification() {
        return new Notification("n1", "bob", NotificationType.FRIEND_REQUEST, "Demande d'ami", "Léa", "/friends/lea",
                null, 1, "lea", AT, null);
    }

    @Test
    @DisplayName("the list is the caller's, paged as asked")
    void list_shouldReadTheCallersPage() {
        when(notificationService.list("bob", AT, 10)).thenReturn(Flux.just(notification()));

        StepVerifier.create(controller.list(jwt, AT, 10))
                .assertNext(dto -> assertThat(dto.id()).isEqualTo("n1"))
                .verifyComplete();
    }

    @Test
    @DisplayName("the unread count is the caller's")
    void unreadCount_shouldCountTheCallers() {
        when(notificationService.unreadCount("bob")).thenReturn(Mono.just(2L));

        StepVerifier.create(controller.unreadCount(jwt)).expectNext(new UnreadCountDTO(2)).verifyComplete();
    }

    @Test
    @DisplayName("each selector marks the caller's notifications its way")
    void markRead_shouldDispatchOnTheSelector() {
        when(notificationService.markAllRead("bob")).thenReturn(Mono.empty());
        when(notificationService.markGroupRead("bob", "c1")).thenReturn(Mono.empty());
        when(notificationService.markRead("bob", List.of("n1"))).thenReturn(Mono.empty());

        StepVerifier.create(controller.markRead(jwt, new ReadRequest(null, null, true))).verifyComplete();
        StepVerifier.create(controller.markRead(jwt, new ReadRequest(null, "c1", null))).verifyComplete();
        StepVerifier.create(controller.markRead(jwt, new ReadRequest(List.of("n1"), null, null))).verifyComplete();

        verify(notificationService).markAllRead("bob");
        verify(notificationService).markGroupRead("bob", "c1");
        verify(notificationService).markRead("bob", List.of("n1"));
    }

    @Test
    @DisplayName("the stream sends unread and notification events, keep-alives, and tells nginx not to buffer")
    void stream_shouldSendNamedEventsAndKeepAlives() {
        when(notificationService.openStream("bob")).thenReturn(Mono.just(Flux.concat(
                Flux.just(new LiveUpdate.UnreadChanged(1), new LiveUpdate.NotificationAdded(notification())),
                Flux.never())));

        ResponseEntity<Flux<ServerSentEvent<Object>>> response = controller.stream(jwt).block();

        assertThat(response.getHeaders().getFirst("X-Accel-Buffering")).isEqualTo("no");
        StepVerifier.create(response.getBody())
                .assertNext(event -> {
                    assertThat(event.event()).isEqualTo(NotificationController.UNREAD_EVENT);
                    assertThat(event.data()).isEqualTo(new UnreadCountDTO(1));
                })
                .assertNext(event -> {
                    assertThat(event.event()).isEqualTo(NotificationController.NOTIFICATION_EVENT);
                    assertThat(((NotificationDTO) event.data()).id()).isEqualTo("n1");
                })
                .assertNext(event -> assertThat(event.comment()).isEqualTo(NotificationController.KEEP_ALIVE_COMMENT))
                .thenCancel()
                .verify(Duration.ofSeconds(2));
    }

    @Test
    @DisplayName("the response ends when the updates do: keep-alives never hold a closed channel open")
    void stream_shouldEndTheResponse_whenTheUpdatesComplete() {
        when(notificationService.openStream("bob"))
                .thenReturn(Mono.just(Flux.concat(Flux.just(new LiveUpdate.UnreadChanged(1)),
                        Mono.delay(Duration.ofMillis(120)).then(Mono.empty()))));

        StepVerifier.create(controller.stream(jwt).flatMapMany(ResponseEntity::getBody))
                .assertNext(event -> assertThat(event.event()).isEqualTo(NotificationController.UNREAD_EVENT))
                .thenConsumeWhile(event -> NotificationController.KEEP_ALIVE_COMMENT.equals(event.comment()))
                .expectComplete()
                .verify(Duration.ofSeconds(2));
    }

    @Test
    @DisplayName("the response ends with the error when the updates fail")
    void stream_shouldEndTheResponseWithTheError_whenTheUpdatesFail() {
        when(notificationService.openStream("bob"))
                .thenReturn(Mono.just(Flux.error(new TechnicalException(TechnicalErrorCode.STORE_UNAVAILABLE))));

        StepVerifier.create(controller.stream(jwt).flatMapMany(ResponseEntity::getBody))
                .expectError(TechnicalException.class)
                .verify(Duration.ofSeconds(2));
    }

    @Test
    @DisplayName("a deleted account fails before any response is written, so the advice answers 410")
    void stream_shouldFailBeforeAnyResponse_whenTheAccountIsErased() {
        when(notificationService.openStream("bob"))
                .thenReturn(Mono.error(new BusinessException(BusinessErrorCode.ACCOUNT_DELETED)));

        StepVerifier.create(controller.stream(jwt))
                .expectErrorSatisfies(error -> assertThat(((BusinessException) error).getErrorCode())
                        .isEqualTo(BusinessErrorCode.ACCOUNT_DELETED))
                .verify();
    }
}
