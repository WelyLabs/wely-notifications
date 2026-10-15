package com.calendar.notifications.infrastructure.messaging.adapters;

import com.calendar.notifications.domain.models.Notification;
import com.calendar.notifications.domain.models.NotificationType;
import com.calendar.notifications.domain.ports.DeletedUserMarkers;
import com.calendar.notifications.domain.ports.NotificationRepository;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.ByteArraySerializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
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
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/** {@code FRIENDSHIP_CHANGED} through a real Kafka into a real MongoDB, after an unreadable record. */
@Testcontainers
@SpringBootTest(properties = {
        "app.consumers.enabled=true",
        "spring.cloud.function.definition=friendships",
        "spring.cloud.stream.bindings.friendships-in-0.destination=FRIENDSHIP_CHANGED_IT",
        "spring.cloud.stream.kafka.binder.auto-create-topics=true",
        "spring.cloud.stream.kafka.bindings.friendships-in-0.consumer.start-offset=earliest",
        "spring.cloud.stream.kafka.bindings.friendships-in-0.consumer.reset-offsets=true"
})
@ActiveProfiles("test")
@EnabledIf("containersRequested")
class FriendshipsConsumerIntegrationTest {

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

    @Autowired
    private NotificationRepository repository;

    @Autowired
    private DeletedUserMarkers markers;

    private static void publish(String... payloads) {
        try (KafkaProducer<String, byte[]> producer = new KafkaProducer<>(Map.of(
                ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, KAFKA.getBootstrapServers(),
                ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class,
                ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, ByteArraySerializer.class))) {
            for (String payload : payloads) {
                producer.send(new ProducerRecord<>("FRIENDSHIP_CHANGED_IT", "bob",
                        payload.getBytes(StandardCharsets.UTF_8)));
            }
        }
    }

    @Test
    @DisplayName("a friend request published on the topic lands in the requested user's notification center")
    void friendships_shouldTurnARecordIntoAStoredNotification() {
        publish("{broken", """
                {"type":"REQUESTED","fromUserId":"lea","fromUserName":"Léa","toUserId":"bob"}""");

        await().atMost(Duration.ofSeconds(60)).untilAsserted(() -> {
            List<Notification> page = repository.findPage("bob", null, 10).collectList().block();
            assertThat(page).hasSize(1);
            assertThat(page.getFirst().type()).isEqualTo(NotificationType.FRIEND_REQUEST);
            assertThat(page.getFirst().body()).isEqualTo("Léa veut être ton ami");
        });
    }

    @Test
    @DisplayName("a late request from a deleted account creates nothing; the next record still notifies")
    void friendships_shouldCreateNothing_forAnErasedRequester() {
        markers.markDeleted("gone", Instant.now()).block();

        publish("""
                {"type":"REQUESTED","fromUserId":"gone","fromUserName":"Gone","toUserId":"dan"}""", """
                {"type":"REQUESTED","fromUserId":"eve","fromUserName":"Ève","toUserId":"dan"}""");

        // Records are read in order: once Ève's request is stored, the deleted one was read and dropped.
        await().atMost(Duration.ofSeconds(60)).untilAsserted(() ->
                assertThat(repository.findPage("dan", null, 10).collectList().block())
                        .extracting(Notification::actorId).containsExactly("eve"));
    }
}
