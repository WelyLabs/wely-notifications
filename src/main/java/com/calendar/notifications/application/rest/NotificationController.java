package com.calendar.notifications.application.rest;

import com.calendar.notifications.application.dtos.NotificationDTO;
import com.calendar.notifications.application.dtos.ReadRequest;
import com.calendar.notifications.application.dtos.UnreadCountDTO;
import com.calendar.notifications.application.mappers.NotificationRestMapper;
import com.calendar.notifications.domain.models.LiveUpdate;
import com.calendar.notifications.domain.services.NotificationService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Signal;

import java.time.Duration;
import java.time.Instant;

/** The caller's notification center: the list, the unread count, marking read, and the live stream. */
@RestController
@RequestMapping("notifications")
public class NotificationController {

    static final String NOTIFICATION_EVENT = "notification";
    static final String UNREAD_EVENT = "unread";
    static final String KEEP_ALIVE_COMMENT = "keep-alive";
    private static final String BUSINESS_ID = "businessId";

    private final NotificationService notificationService;
    private final NotificationRestMapper mapper;
    private final Duration keepAlive;

    public NotificationController(NotificationService notificationService, NotificationRestMapper mapper,
                                  @Value("${app.stream.keep-alive:20s}") Duration keepAlive) {
        this.notificationService = notificationService;
        this.mapper = mapper;
        this.keepAlive = keepAlive;
    }

    /** Newest first; pass the last {@code createdAt} as {@code before} for the next page. */
    @GetMapping
    public Flux<NotificationDTO> list(@AuthenticationPrincipal Jwt jwt,
                                      @RequestParam(required = false)
                                      @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant before,
                                      @RequestParam(required = false) Integer limit) {
        return notificationService.list(jwt.getClaimAsString(BUSINESS_ID), before, limit).map(mapper::toDto);
    }

    @GetMapping("/unread-count")
    public Mono<UnreadCountDTO> unreadCount(@AuthenticationPrincipal Jwt jwt) {
        return notificationService.unreadCount(jwt.getClaimAsString(BUSINESS_ID)).map(mapper::toUnreadCount);
    }

    /** Only ever the caller's notifications, whatever ids are sent. */
    @PostMapping("/read")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public Mono<Void> markRead(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody ReadRequest request) {
        String userId = jwt.getClaimAsString(BUSINESS_ID);
        if (Boolean.TRUE.equals(request.all())) {
            return notificationService.markAllRead(userId);
        }
        if (request.groupKey() != null && !request.groupKey().isBlank()) {
            return notificationService.markGroupRead(userId, request.groupKey());
        }
        return notificationService.markRead(userId, request.ids());
    }

    /** The unread count, then {@code notification} and {@code unread} events; 410 for a deleted account. */
    @GetMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Mono<ResponseEntity<Flux<ServerSentEvent<Object>>>> stream(@AuthenticationPrincipal Jwt jwt) {
        return notificationService.openStream(jwt.getClaimAsString(BUSINESS_ID))
                .map(updates -> ResponseEntity.ok()
                        .header("X-Accel-Buffering", "no")
                        .body(withKeepAlives(updates.map(this::toEvent))));
    }

    // Merged as signals: the updates ending, or failing, ends the response; the endless ticks never hold it open.
    private Flux<ServerSentEvent<Object>> withKeepAlives(Flux<ServerSentEvent<Object>> events) {
        Flux<Signal<ServerSentEvent<Object>>> keepAlives = Flux.interval(keepAlive)
                .map(tick -> Signal.next(ServerSentEvent.<Object>builder().comment(KEEP_ALIVE_COMMENT).build()));
        return Flux.merge(events.materialize(), keepAlives).dematerialize();
    }

    private ServerSentEvent<Object> toEvent(LiveUpdate update) {
        return switch (update) {
            case LiveUpdate.NotificationAdded added -> ServerSentEvent.<Object>builder(mapper.toDto(added.notification()))
                    .event(NOTIFICATION_EVENT)
                    .build();
            case LiveUpdate.UnreadChanged unread -> ServerSentEvent.<Object>builder(mapper.toUnreadCount(unread.count()))
                    .event(UNREAD_EVENT)
                    .build();
        };
    }
}
