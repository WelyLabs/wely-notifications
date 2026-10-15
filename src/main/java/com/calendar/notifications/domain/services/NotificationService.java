package com.calendar.notifications.domain.services;

import com.calendar.notifications.domain.models.LiveUpdate;
import com.calendar.notifications.domain.models.Notification;
import com.calendar.notifications.domain.ports.LiveNotifications;
import com.calendar.notifications.domain.ports.NotificationRepository;
import com.calendar.notifications.domain.ports.NotificationWording;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Clock;
import java.time.Instant;
import java.util.Collection;

/**
 * The notification center: records each notification, tells the recipient's open clients, and hands
 * it to {@link PushService} for their devices.
 */
public class NotificationService {

    static final int DEFAULT_PAGE_SIZE = 30;
    static final int MAX_PAGE_SIZE = 100;

    private final NotificationRepository repository;
    private final LiveNotifications live;
    private final PushService pushService;
    private final NotificationWording wording;
    private final Clock clock;

    public NotificationService(NotificationRepository repository, LiveNotifications live, PushService pushService,
                               NotificationWording wording, Clock clock) {
        this.repository = repository;
        this.live = live;
        this.pushService = pushService;
        this.wording = wording;
        this.clock = clock;
    }

    /**
     * Records the notification, or merges it into the recipient's unread one of the same conversation,
     * then sends it live and as push. Push failures never fail this.
     */
    public Mono<Notification> notify(Notification draft) {
        Notification stamped = draft.withCreatedAt(clock.instant());
        Mono<Notification> saved = stamped.groupable() ? repository.group(stamped) : repository.insert(stamped);
        return saved
                .map(this::present)
                .flatMap(notification -> {
                    live.publish(notification.recipientId(), new LiveUpdate.NotificationAdded(notification));
                    return publishUnreadCount(notification.recipientId())
                            .then(pushService.deliver(notification))
                            .thenReturn(notification);
                });
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
        return repository.markRead(recipientId, ids, clock.instant()).then(publishUnreadCount(recipientId));
    }

    /** Opening a conversation reads its notifications. */
    public Mono<Void> markGroupRead(String recipientId, String groupKey) {
        return repository.markGroupRead(recipientId, groupKey, clock.instant()).then(publishUnreadCount(recipientId));
    }

    public Mono<Void> markAllRead(String recipientId) {
        return repository.markAllRead(recipientId, clock.instant()).then(publishUnreadCount(recipientId));
    }

    /** The unread count now, then each new notification and count change while connected. */
    public Flux<LiveUpdate> stream(String recipientId) {
        Mono<LiveUpdate> initial = repository.countUnread(recipientId).map(LiveUpdate.UnreadChanged::new);
        return Flux.merge(initial, live.stream(recipientId));
    }

    private Mono<Void> publishUnreadCount(String recipientId) {
        return repository.countUnread(recipientId)
                .doOnNext(count -> live.publish(recipientId, new LiveUpdate.UnreadChanged(count)))
                .then();
    }

    private Notification present(Notification notification) {
        return notification.withTitle(wording.title(notification));
    }
}
