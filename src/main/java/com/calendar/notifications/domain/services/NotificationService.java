package com.calendar.notifications.domain.services;

import com.calendar.notifications.domain.models.LiveUpdate;
import com.calendar.notifications.domain.models.Notification;
import com.calendar.notifications.domain.ports.DeletedUserMarkers;
import com.calendar.notifications.domain.ports.LiveNotifications;
import com.calendar.notifications.domain.ports.NotificationRepository;
import com.calendar.notifications.domain.ports.NotificationWording;
import com.calendar.notifications.exception.NotificationsErrorCode;
import com.calendar.notifications.exception.NotificationsException;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Clock;
import java.time.Instant;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.stream.Stream;

/** The notification center: records each notification, streams it live and hands it to {@link PushService}. */
public class NotificationService {

    static final int DEFAULT_PAGE_SIZE = 30;
    static final int MAX_PAGE_SIZE = 100;

    private final NotificationRepository repository;
    private final LiveNotifications live;
    private final PushService pushService;
    private final NotificationWording wording;
    private final DeletedUserMarkers markers;
    private final Clock clock;

    public NotificationService(NotificationRepository repository, LiveNotifications live, PushService pushService,
                               NotificationWording wording, DeletedUserMarkers markers, Clock clock) {
        this.repository = repository;
        this.live = live;
        this.pushService = pushService;
        this.wording = wording;
        this.markers = markers;
        this.clock = clock;
    }

    /** Records, streams and pushes the notification; empty when the recipient or the actor is deleted. */
    public Mono<Notification> notify(Notification draft) {
        return markers.anyDeleted(involved(draft))
                .flatMap(deleted -> deleted ? Mono.<Notification>empty() : record(draft));
    }

    /** Newest first, before the given time when paging; the page size is kept between 1 and 100. */
    public Flux<Notification> list(String recipientId, Instant before, Integer limit) {
        int size = limit == null ? DEFAULT_PAGE_SIZE : Math.clamp(limit, 1, MAX_PAGE_SIZE);
        return repository.findPage(recipientId, before, size).map(this::present);
    }

    public Mono<Long> unreadCount(String recipientId) {
        return repository.countUnread(recipientId);
    }

    public Mono<Void> markRead(String recipientId, Collection<String> ids) {
        return repository.markRead(recipientId, ids, clock.instant()).then(announceUnreadCount(recipientId));
    }

    /** Opening a conversation reads its notifications. */
    public Mono<Void> markGroupRead(String recipientId, String groupKey) {
        return repository.markGroupRead(recipientId, groupKey, clock.instant()).then(announceUnreadCount(recipientId));
    }

    public Mono<Void> markAllRead(String recipientId) {
        return repository.markAllRead(recipientId, clock.instant()).then(announceUnreadCount(recipientId));
    }

    /** The unread count, then each live update; fails with ACCOUNT_DELETED for a deleted account. */
    public Mono<Flux<LiveUpdate>> openStream(String recipientId) {
        return isDeleted(recipientId)
                .flatMap(deleted -> deleted
                        ? Mono.error(new NotificationsException(NotificationsErrorCode.ACCOUNT_DELETED))
                        : Mono.just(updates(recipientId)));
    }

    /** Tells the recipient's open clients their unread count. */
    public Mono<Void> announceUnreadCount(String recipientId) {
        return repository.countUnread(recipientId)
                .doOnNext(count -> live.publish(recipientId, new LiveUpdate.UnreadChanged(count)))
                .then();
    }

    private Mono<Notification> record(Notification draft) {
        Notification stamped = draft.withCreatedAt(clock.instant());
        Mono<Notification> saved = stamped.groupable() ? repository.group(stamped) : repository.insert(stamped);
        return saved
                .flatMap(this::unlessErasedMeanwhile)
                .map(this::present)
                .flatMap(notification -> {
                    live.publish(notification.recipientId(), new LiveUpdate.NotificationAdded(notification));
                    return announceUnreadCount(notification.recipientId())
                            .then(pushService.deliver(notification))
                            .thenReturn(notification);
                });
    }

    // Checked again after the write: an erasure that started meanwhile may have missed this document.
    private Mono<Notification> unlessErasedMeanwhile(Notification saved) {
        return markers.anyDeleted(involved(saved))
                .flatMap(deleted -> deleted
                        ? repository.deleteOwned(saved.recipientId(), saved.id())
                                .then(Mono.defer(() -> announceUnreadCount(saved.recipientId())))
                                .then(Mono.<Notification>empty())
                        : Mono.just(saved));
    }

    // merge subscribes in order, so the marker is read again after joining: an erasure just before is caught.
    private Flux<LiveUpdate> updates(String recipientId) {
        Mono<LiveUpdate> initial = repository.countUnread(recipientId).map(LiveUpdate.UnreadChanged::new);
        Mono<LiveUpdate> closeIfDeleted = Mono.defer(() -> isDeleted(recipientId))
                .filter(Boolean::booleanValue)
                .doOnNext(deleted -> live.close(recipientId))
                .then(Mono.empty());
        return Flux.merge(initial, live.stream(recipientId), closeIfDeleted);
    }

    private Mono<Boolean> isDeleted(String userId) {
        return markers.anyDeleted(Collections.singletonList(userId));
    }

    private static List<String> involved(Notification notification) {
        return Stream.of(notification.recipientId(), notification.actorId())
                .filter(Objects::nonNull)
                .distinct()
                .toList();
    }

    private Notification present(Notification notification) {
        return notification.withTitle(wording.title(notification));
    }
}
