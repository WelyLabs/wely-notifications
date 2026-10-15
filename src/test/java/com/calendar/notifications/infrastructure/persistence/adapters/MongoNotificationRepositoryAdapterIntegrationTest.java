package com.calendar.notifications.infrastructure.persistence.adapters;

import com.calendar.notifications.domain.models.Notification;
import com.calendar.notifications.domain.models.NotificationType;
import com.calendar.notifications.infrastructure.persistence.entities.NotificationEntity;
import org.bson.Document;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.data.mongodb.core.ReactiveMongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.containers.MongoDBContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import reactor.core.publisher.Flux;
import reactor.core.scheduler.Schedulers;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/** Notifications against a real MongoDB: atomic grouping, paging, recipient scoping and erasure. */
@Testcontainers
@SpringBootTest(properties = "spring.data.mongodb.auto-index-creation=true")
@ActiveProfiles("test")
@EnabledIf("containersRequested")
class MongoNotificationRepositoryAdapterIntegrationTest {

    @Container
    @ServiceConnection
    static final MongoDBContainer MONGO = new MongoDBContainer("mongo:6.0");

    static boolean containersRequested() {
        return System.getenv("CI") != null || Boolean.getBoolean("integration.tests");
    }

    // Millisecond precision: what MongoDB stores, so instants compare equal on the way back.
    private static final Instant NOW = Instant.now().truncatedTo(ChronoUnit.MILLIS);

    @Autowired
    private MongoNotificationRepositoryAdapter adapter;

    @Autowired
    private ReactiveMongoTemplate mongoTemplate;

    @BeforeEach
    void clear() {
        mongoTemplate.remove(new Query(), NotificationEntity.class).block();
    }

    private static Notification message(String recipient, String conversation, String body, Instant at) {
        return Notification.draft(recipient, NotificationType.MESSAGE, "Léa", body, "/chat/" + conversation,
                conversation, "lea").withCreatedAt(at);
    }

    private static Notification friendRequest(String recipient, Instant at) {
        return Notification.draft(recipient, NotificationType.FRIEND_REQUEST, "Demande d'ami", "Léa", "/friends/lea",
                null, "lea").withCreatedAt(at);
    }

    private static Notification messageFrom(String actor, String recipient, String conversation, Instant at) {
        return Notification.draft(recipient, NotificationType.MESSAGE, actor, "Salut", "/chat/" + conversation,
                conversation, actor).withCreatedAt(at);
    }

    private List<Notification> centerOf(String recipient) {
        return adapter.findPage(recipient, null, 50).collectList().block();
    }

    @Test
    @DisplayName("a second message of a conversation merges: one notification, count 2, latest excerpt and time")
    void group_shouldMergeIntoTheOpenNotification() {
        Notification first = adapter.group(message("bob", "c1", "Salut", NOW.minusSeconds(60))).block();
        Notification second = adapter.group(message("bob", "c1", "Tu viens ?", NOW)).block();

        assertThat(second.id()).isEqualTo(first.id());
        assertThat(second.count()).isEqualTo(2);
        assertThat(second.body()).isEqualTo("Tu viens ?");
        assertThat(second.createdAt()).isEqualTo(NOW);
        assertThat(adapter.countUnread("bob").block()).isEqualTo(1L);
    }

    @Test
    @DisplayName("messages racing on one conversation still make one notification, counting every message")
    void group_shouldStayAtomicUnderConcurrentMessages() {
        int messages = 20;

        Flux.range(0, messages)
                .flatMap(i -> adapter.group(message("bob", "c1", "m" + i, NOW)).subscribeOn(Schedulers.parallel()))
                .blockLast(Duration.ofSeconds(20));

        List<Notification> page = adapter.findPage("bob", null, 50).collectList().block();
        assertThat(page).hasSize(1);
        assertThat(page.getFirst().count()).isEqualTo(messages);
    }

    @Test
    @DisplayName("once read, the group closes: the next message opens a new notification")
    void markGroupRead_shouldCloseTheGroup() {
        Notification first = adapter.group(message("bob", "c1", "Salut", NOW.minusSeconds(60))).block();

        assertThat(adapter.markGroupRead("bob", "c1", NOW).block()).isEqualTo(1L);
        Notification next = adapter.group(message("bob", "c1", "Re", NOW)).block();

        assertThat(next.id()).isNotEqualTo(first.id());
        assertThat(next.count()).isEqualTo(1);
        assertThat(adapter.countUnread("bob").block()).isEqualTo(1L);
    }

    @Test
    @DisplayName("each recipient and each conversation has its own group")
    void group_shouldKeepGroupsApart() {
        adapter.group(message("bob", "c1", "a", NOW)).block();
        adapter.group(message("bob", "c2", "b", NOW)).block();
        adapter.group(message("carl", "c1", "c", NOW)).block();

        assertThat(adapter.countUnread("bob").block()).isEqualTo(2L);
        assertThat(adapter.countUnread("carl").block()).isEqualTo(1L);
    }

    @Test
    @DisplayName("pages come newest first, before the cursor when given, and only the recipient's")
    void findPage_shouldPageNewestFirstForTheRecipient() {
        for (int i = 0; i < 5; i++) {
            adapter.insert(friendRequest("bob", NOW.minusSeconds(i * 10L))).block();
        }
        adapter.insert(friendRequest("carl", NOW)).block();

        List<Notification> first = adapter.findPage("bob", null, 2).collectList().block();
        List<Notification> next = adapter.findPage("bob", first.getLast().createdAt(), 10).collectList().block();

        assertThat(first).extracting(Notification::createdAt).containsExactly(NOW, NOW.minusSeconds(10));
        assertThat(next).extracting(Notification::createdAt)
                .containsExactly(NOW.minusSeconds(20), NOW.minusSeconds(30), NOW.minusSeconds(40));
        assertThat(next).extracting(Notification::recipientId).containsOnly("bob");
    }

    @Test
    @DisplayName("marking by id touches only the caller's notifications")
    void markRead_shouldIgnoreSomeoneElsesIds() {
        Notification mine = adapter.insert(friendRequest("bob", NOW)).block();
        Notification theirs = adapter.insert(friendRequest("carl", NOW)).block();

        assertThat(adapter.markRead("bob", List.of(mine.id(), theirs.id()), NOW).block()).isEqualTo(1L);

        assertThat(adapter.countUnread("bob").block()).isZero();
        assertThat(adapter.countUnread("carl").block()).isEqualTo(1L);
        assertThat(adapter.findPage("bob", null, 1).blockFirst().readAt()).isEqualTo(NOW);
    }

    @Test
    @DisplayName("marking all reads every unread notification of the caller, and only theirs")
    void markAllRead_shouldReadEverythingOfTheCaller() {
        adapter.insert(friendRequest("bob", NOW)).block();
        adapter.group(message("bob", "c1", "x", NOW)).block();
        adapter.insert(friendRequest("carl", NOW)).block();

        assertThat(adapter.markAllRead("bob", NOW).block()).isEqualTo(2L);
        assertThat(adapter.countUnread("bob").block()).isZero();
        assertThat(adapter.countUnread("carl").block()).isEqualTo(1L);
    }

    @Test
    @DisplayName("clearing a center deletes every notification of that recipient, and only theirs")
    void deleteByRecipient_shouldLeaveOtherRecipientsAlone() {
        adapter.insert(friendRequest("lea", NOW)).block();
        adapter.group(message("lea", "c1", "x", NOW)).block();
        adapter.insert(friendRequest("bob", NOW)).block();

        assertThat(adapter.deleteByRecipient("lea").block()).isEqualTo(2L);

        assertThat(centerOf("lea")).isEmpty();
        assertThat(centerOf("bob")).hasSize(1);
    }

    @Test
    @DisplayName("deleting by actor clears that actor from every center and names who lost a notification")
    void deleteByActor_shouldDeleteInEveryCenterAndNameTheRecipients() {
        adapter.insert(friendRequest("bob", NOW)).block();
        adapter.group(messageFrom("lea", "carl", "c2", NOW)).block();
        adapter.group(messageFrom("max", "carl", "c3", NOW)).block();

        assertThat(adapter.deleteByActor("lea").block()).isEqualTo(Set.of("bob", "carl"));

        assertThat(centerOf("bob")).isEmpty();
        assertThat(centerOf("carl")).extracting(Notification::actorId).containsExactly("max");
    }

    @Test
    @DisplayName("a merged group whose latest message is the actor's goes, the one someone else spoke last in stays")
    void deleteByActor_shouldDeleteAMergedGroupWhoseLatestActorIsTheUser() {
        adapter.group(messageFrom("max", "bob", "group", NOW.minusSeconds(60))).block();
        adapter.group(messageFrom("lea", "bob", "group", NOW)).block();
        adapter.group(messageFrom("lea", "carl", "group", NOW.minusSeconds(60))).block();
        adapter.group(messageFrom("max", "carl", "group", NOW)).block();

        assertThat(adapter.deleteByActor("lea").block()).isEqualTo(Set.of("bob"));

        assertThat(centerOf("bob")).isEmpty();
        assertThat(centerOf("carl")).singleElement().satisfies(left -> {
            assertThat(left.actorId()).isEqualTo("max");
            assertThat(left.count()).isEqualTo(2);
        });
    }

    @Test
    @DisplayName("removing a notification by id needs it to be the recipient's")
    void deleteOwned_shouldNotDeleteAnotherRecipientsNotification() {
        Notification bobs = adapter.insert(friendRequest("bob", NOW)).block();

        adapter.deleteOwned("carl", bobs.id()).block();
        assertThat(centerOf("bob")).hasSize(1);

        adapter.deleteOwned("bob", bobs.id()).block();
        assertThat(centerOf("bob")).isEmpty();
    }

    @Test
    @DisplayName("the actor index exists, so erasing an account is no collection scan")
    void actorIndex_shouldExist() {
        adapter.insert(friendRequest("bob", NOW)).block();

        Document index = mongoTemplate.getCollection("notifications")
                .flatMapMany(collection -> collection.listIndexes())
                .filter(candidate -> "actor".equals(candidate.getString("name")))
                .blockFirst(Duration.ofSeconds(10));

        assertThat(index).isNotNull();
        assertThat(index.get("key", Document.class)).containsKey("actorId");
    }

    @Test
    @DisplayName("the open-group index is unique and partial, so read notifications never collide")
    void openGroupIndex_shouldBeUniqueAndPartial() {
        adapter.insert(friendRequest("bob", NOW)).block();

        Document index = mongoTemplate.getCollection("notifications")
                .flatMapMany(collection -> collection.listIndexes())
                .filter(candidate -> "recipient_open_group".equals(candidate.getString("name")))
                .blockFirst(Duration.ofSeconds(10));

        assertThat(index).isNotNull();
        assertThat(index.getBoolean("unique")).isTrue();
        assertThat(index.get("partialFilterExpression", Document.class)).containsKey("openGroupKey");
    }
}
