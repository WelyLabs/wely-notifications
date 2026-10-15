package com.calendar.notifications.infrastructure.messaging.adapters;

import com.calendar.notifications.domain.models.Notification;
import com.calendar.notifications.domain.models.NotificationCategory;
import com.calendar.notifications.domain.models.NotificationPreferences;
import com.calendar.notifications.domain.models.NotificationType;
import com.calendar.notifications.domain.models.PushSubscription;
import com.calendar.notifications.domain.ports.NotificationRepository;
import com.calendar.notifications.domain.ports.PreferencesRepository;
import com.calendar.notifications.domain.ports.SubscriptionRepository;
import com.calendar.notifications.infrastructure.persistence.entities.DeletedUserEntity;
import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.AdminClientConfig;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.ByteArraySerializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.cloud.stream.binding.BindingService;
import org.springframework.data.mongodb.core.ReactiveMongoTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.MongoDBContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.kafka.KafkaContainer;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/** {@code USER_DELETED} through a real Kafka into a real MongoDB, on a topic the binder may not create. */
@Testcontainers
@SpringBootTest(properties = {
        "app.consumers.enabled=true",
        "spring.cloud.function.definition=userDeleted",
        "spring.cloud.stream.bindings.userDeleted-in-0.destination=" + UserDeletedConsumerIntegrationTest.TOPIC,
        "spring.cloud.stream.kafka.binder.auto-create-topics=false"
})
@ActiveProfiles("test")
@EnabledIf("containersRequested")
class UserDeletedConsumerIntegrationTest {

    static final String TOPIC = "USER_DELETED_IT";
    private static final Instant NOW = Instant.now().truncatedTo(ChronoUnit.MILLIS);
    private static final Instant DELETED_AT = Instant.parse("2026-10-15T14:03:27.123Z");
    private static final String P256DH =
            "BCVxsr7N_eNgVRqvHtD0zTZsEc6-VV-JvLexhqUzORcxaOzi6-AYWXvTBHm4bjyPjs7Vd8pZGH6SRpkNtoIAiw4";
    private static final String AUTH = "BTBZMqHH6r4Tts7J_aSIgg";

    @Container
    @ServiceConnection
    static final MongoDBContainer MONGO = new MongoDBContainer("mongo:6.0");

    @Container
    static final KafkaContainer KAFKA = new KafkaContainer("apache/kafka:3.8.0");

    @DynamicPropertySource
    static void bindTheBrokerToTheBinder(DynamicPropertyRegistry registry) {
        registry.add("spring.cloud.stream.kafka.binder.brokers", KAFKA::getBootstrapServers);
    }

    static boolean containersRequested() {
        return System.getenv("CI") != null || Boolean.getBoolean("integration.tests");
    }

    // Before the context: the binder is not allowed to create topics.
    @BeforeAll
    static void createTheTopic() throws Exception {
        try (AdminClient admin = AdminClient.create(Map.of(
                AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG, KAFKA.getBootstrapServers()))) {
            admin.createTopics(List.of(new NewTopic(TOPIC, 1, (short) 1))).all().get();
        }
    }

    @Autowired
    private NotificationRepository notifications;

    @Autowired
    private SubscriptionRepository subscriptions;

    @Autowired
    private PreferencesRepository preferences;

    @Autowired
    private ReactiveMongoTemplate mongoTemplate;

    @Autowired
    private BindingService bindingService;

    private static void publish(String... payloads) {
        try (KafkaProducer<String, byte[]> producer = new KafkaProducer<>(Map.of(
                ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, KAFKA.getBootstrapServers(),
                ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class,
                ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, ByteArraySerializer.class))) {
            for (String payload : payloads) {
                producer.send(new ProducerRecord<>(TOPIC, payload.getBytes(StandardCharsets.UTF_8)));
            }
        }
    }

    private static String deletion(String userId, Instant deletedAt) {
        return "{\"userId\":\"" + userId + "\",\"deletedAt\":\"" + deletedAt + "\"}";
    }

    private DeletedUserEntity marker(String userId) {
        return mongoTemplate.findById(userId, DeletedUserEntity.class).block();
    }

    private void awaitErased(String userId) {
        await().atMost(Duration.ofSeconds(60)).untilAsserted(() -> {
            DeletedUserEntity marker = marker(userId);
            assertThat(marker).isNotNull();
            assertThat(marker.erasedAt()).isNotNull();
        });
    }

    private static Notification friendRequest(String from, String to) {
        return Notification.draft(to, NotificationType.FRIEND_REQUEST, "Demande d'ami", from + " veut être ton ami",
                "/friends/" + from, null, from).withCreatedAt(NOW);
    }

    @Test
    @DisplayName("the function returning Mono<Void> is bound as an input only: no output binding is created")
    void userDeleted_shouldBindNoOutput() {
        assertThat(bindingService.getConsumerBindingNames()).contains("userDeleted-in-0");
        assertThat(bindingService.getProducerBindingNames()).doesNotContain("userDeleted-out-0");
    }

    @Test
    @DisplayName("a deletion on the topic erases the user's center, devices and preferences, and what they caused elsewhere")
    void userDeleted_shouldEraseWhatTheTopicNames() {
        notifications.insert(friendRequest("bob", "lea")).block();
        notifications.insert(friendRequest("lea", "bob")).block();
        notifications.insert(friendRequest("carl", "bob")).block();
        subscriptions.save(new PushSubscription("lea", "https://push.example/lea", P256DH, AUTH, null, NOW)).block();
        preferences.save(new NotificationPreferences("lea", Map.of(NotificationCategory.CALLS, false))).block();

        publish("{broken", deletion("lea", DELETED_AT));

        awaitErased("lea");
        assertThat(marker("lea").deletedAt()).isEqualTo(DELETED_AT);
        assertThat(notifications.findPage("lea", null, 10).collectList().block()).isEmpty();
        assertThat(notifications.findPage("bob", null, 10).collectList().block())
                .extracting(Notification::actorId).containsExactly("carl");
        assertThat(subscriptions.findByUser("lea").collectList().block()).isEmpty();
        assertThat(preferences.find("lea").block()).isNull();
    }

    @Test
    @DisplayName("a replayed deletion changes nothing: the marker keeps its first deletion time")
    void userDeleted_shouldBeANoOp_whenTheRecordIsReplayed() {
        publish(deletion("max", DELETED_AT));
        awaitErased("max");

        // The next user's erasure proves the replay before it was read: records are handled in order.
        publish(deletion("max", DELETED_AT.plusSeconds(3600)), deletion("zoe", DELETED_AT));
        awaitErased("zoe");

        assertThat(marker("max").deletedAt()).isEqualTo(DELETED_AT);
    }
}
