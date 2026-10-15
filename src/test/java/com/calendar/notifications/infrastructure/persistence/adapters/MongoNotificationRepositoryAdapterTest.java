package com.calendar.notifications.infrastructure.persistence.adapters;

import com.calendar.notifications.domain.models.Notification;
import com.calendar.notifications.domain.models.NotificationType;
import com.calendar.notifications.exception.NotificationsErrorCode;
import com.calendar.notifications.exception.NotificationsException;
import com.calendar.notifications.infrastructure.persistence.entities.NotificationEntity;
import com.calendar.notifications.infrastructure.persistence.mappers.NotificationPersistenceMapper;
import com.calendar.notifications.infrastructure.persistence.repositories.NotificationMongoRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mapstruct.factory.Mappers;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.domain.Limit;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** The adapter's own logic against a mocked repository; the queries themselves run in the integration test. */
@ExtendWith(MockitoExtension.class)
class MongoNotificationRepositoryAdapterTest {

    private static final Instant NOW = Instant.parse("2026-10-15T10:00:00Z");

    @Mock
    private NotificationMongoRepository repository;

    private MongoNotificationRepositoryAdapter adapter;

    @BeforeEach
    void setUp() {
        adapter = new MongoNotificationRepositoryAdapter(repository, Mappers.getMapper(NotificationPersistenceMapper.class));
    }

    private static Notification message() {
        return Notification.draft("bob", NotificationType.MESSAGE, "Léa", "Salut", "/chat/c1", "c1", "lea")
                .withCreatedAt(NOW);
    }

    private static NotificationEntity open(int count) {
        return new NotificationEntity("n1", "bob", NotificationType.MESSAGE, "Léa", "Salut", "/chat/c1", "c1", "c1",
                count, "lea", NOW, null);
    }

    private static DataAccessResourceFailureException down() {
        return new DataAccessResourceFailureException("mongo down");
    }

    @Test
    @DisplayName("a new notification gets a UUID, and a groupable one is stored open to merging")
    void insert_shouldGiveAnIdAndOpenTheGroup() {
        when(repository.insert(any(NotificationEntity.class))).thenAnswer(call -> Mono.just(call.getArgument(0)));

        StepVerifier.create(adapter.insert(message()))
                .assertNext(saved -> assertThat(saved.id()).hasSize(36))
                .verifyComplete();

        ArgumentCaptor<NotificationEntity> stored = ArgumentCaptor.forClass(NotificationEntity.class);
        verify(repository).insert(stored.capture());
        assertThat(stored.getValue().openGroupKey()).isEqualTo("c1");
    }

    @Test
    @DisplayName("an open group takes the message and is returned merged")
    void group_shouldMergeIntoTheOpenGroup() {
        when(repository.mergeIntoOpenGroup("bob", "c1", "Léa", "Salut", "/chat/c1", "lea", NOW)).thenReturn(Mono.just(1L));
        when(repository.findOpenGroup("bob", "c1")).thenReturn(Mono.just(open(2)));

        StepVerifier.create(adapter.group(message()))
                .assertNext(merged -> assertThat(merged.count()).isEqualTo(2))
                .verifyComplete();

        verify(repository, never()).insert(any(NotificationEntity.class));
    }

    @Test
    @DisplayName("without an open group, the message opens one")
    void group_shouldInsert_whenNoGroupIsOpen() {
        when(repository.mergeIntoOpenGroup(any(), any(), any(), any(), any(), any(), any())).thenReturn(Mono.just(0L));
        when(repository.insert(any(NotificationEntity.class))).thenAnswer(call -> Mono.just(call.getArgument(0)));

        StepVerifier.create(adapter.group(message()))
                .assertNext(created -> assertThat(created.count()).isEqualTo(1))
                .verifyComplete();
    }

    @Test
    @DisplayName("losing the insert race to a concurrent message merges into the winner's group")
    void group_shouldMerge_whenAConcurrentInsertWon() {
        when(repository.mergeIntoOpenGroup(any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(Mono.just(0L), Mono.just(1L));
        when(repository.insert(any(NotificationEntity.class))).thenReturn(Mono.error(new DuplicateKeyException("dup")));
        when(repository.findOpenGroup("bob", "c1")).thenReturn(Mono.just(open(2)));

        StepVerifier.create(adapter.group(message()))
                .assertNext(merged -> assertThat(merged.count()).isEqualTo(2))
                .verifyComplete();

        verify(repository, times(2)).mergeIntoOpenGroup(any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("the first page reads the latest; the next ones read before a time")
    void findPage_shouldPickTheQueryByCursor() {
        when(repository.findLatest("bob", Limit.of(30))).thenReturn(Flux.just(open(1)));
        when(repository.findLatestBefore("bob", NOW, Limit.of(30))).thenReturn(Flux.empty());

        StepVerifier.create(adapter.findPage("bob", null, 30)).expectNextCount(1).verifyComplete();
        StepVerifier.create(adapter.findPage("bob", NOW, 30)).verifyComplete();
    }

    @Test
    @DisplayName("counts and marks go straight to the recipient-scoped queries")
    void countAndMarks_shouldUseTheScopedQueries() {
        when(repository.countUnread("bob")).thenReturn(Mono.just(3L));
        when(repository.markRead("bob", List.of("n1"), NOW)).thenReturn(Mono.just(1L));
        when(repository.markGroupRead("bob", "c1", NOW)).thenReturn(Mono.just(2L));
        when(repository.markAllRead("bob", NOW)).thenReturn(Mono.just(4L));

        StepVerifier.create(adapter.countUnread("bob")).expectNext(3L).verifyComplete();
        StepVerifier.create(adapter.markRead("bob", List.of("n1"), NOW)).expectNext(1L).verifyComplete();
        StepVerifier.create(adapter.markGroupRead("bob", "c1", NOW)).expectNext(2L).verifyComplete();
        StepVerifier.create(adapter.markAllRead("bob", NOW)).expectNext(4L).verifyComplete();
    }

    @Test
    @DisplayName("a MongoDB failure reaches the domain as NTF-TEC-001, on every method")
    void everyMethod_shouldTranslateStoreFailures() {
        when(repository.insert(any(NotificationEntity.class))).thenReturn(Mono.error(down()));
        when(repository.mergeIntoOpenGroup(any(), any(), any(), any(), any(), any(), any())).thenReturn(Mono.error(down()));
        when(repository.findLatest(any(), any())).thenReturn(Flux.error(down()));
        when(repository.countUnread(any())).thenReturn(Mono.error(down()));
        when(repository.markRead(any(), any(), any())).thenReturn(Mono.error(down()));
        when(repository.markGroupRead(any(), any(), any())).thenReturn(Mono.error(down()));
        when(repository.markAllRead(any(), any())).thenReturn(Mono.error(down()));

        assertStoreUnavailable(adapter.insert(message()));
        assertStoreUnavailable(adapter.group(message()));
        assertStoreUnavailable(adapter.findPage("bob", null, 30).then());
        assertStoreUnavailable(adapter.countUnread("bob"));
        assertStoreUnavailable(adapter.markRead("bob", List.of("n1"), NOW));
        assertStoreUnavailable(adapter.markGroupRead("bob", "c1", NOW));
        assertStoreUnavailable(adapter.markAllRead("bob", NOW));
    }

    static void assertStoreUnavailable(Mono<?> call) {
        StepVerifier.create(call)
                .expectErrorSatisfies(error -> assertThat(error)
                        .isInstanceOfSatisfying(NotificationsException.class, failure ->
                                assertThat(failure.getErrorCode()).isEqualTo(NotificationsErrorCode.STORE_UNAVAILABLE)))
                .verify();
    }
}
