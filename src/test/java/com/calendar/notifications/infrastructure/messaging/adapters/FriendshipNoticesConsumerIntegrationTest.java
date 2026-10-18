package com.calendar.notifications.infrastructure.messaging.adapters;

import com.calendar.notifications.domain.models.Notification;
import com.calendar.notifications.domain.models.NotificationType;
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
import org.springframework.cloud.stream.binding.BindingService;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.MongoDBContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.kafka.KafkaContainer;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/** {@code FRIENDSHIP_NOTICES} through a real Kafka into a real MongoDB, after an unreadable record. */
@Testcontainers
@SpringBootTest(properties = {
        "app.consumers.enabled=true",
        "spring.cloud.function.definition=friendshipNotices",
        "spring.cloud.stream.bindings.friendshipNotices-in-0.destination=FRIENDSHIP_NOTICES_IT",
        "spring.cloud.stream.kafka.binder.auto-create-topics=true",
        "spring.cloud.stream.kafka.bindings.friendshipNotices-in-0.consumer.start-offset=earliest",
        "spring.cloud.stream.kafka.bindings.friendshipNotices-in-0.consumer.reset-offsets=true"
})
@ActiveProfiles("test")
@EnabledIf("containersRequested")
class FriendshipNoticesConsumerIntegrationTest {

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
    private BindingService bindingService;

    private static void publish(String... payloads) {
        try (KafkaProducer<String, byte[]> producer = new KafkaProducer<>(Map.of(
                ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, KAFKA.getBootstrapServers(),
                ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class,
                ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, ByteArraySerializer.class))) {
            for (String payload : payloads) {
                producer.send(new ProducerRecord<>("FRIENDSHIP_NOTICES_IT", "bob",
                        payload.getBytes(StandardCharsets.UTF_8)));
            }
        }
    }

    @Test
    @DisplayName("the function returning Mono<Void> is bound as an input only: no output binding is created")
    void friendshipNotices_shouldBindNoOutput() {
        assertThat(bindingService.getConsumerBindingNames()).contains("friendshipNotices-in-0");
        assertThat(bindingService.getProducerBindingNames()).doesNotContain("friendshipNotices-out-0");
    }

    @Test
    @DisplayName("a reunion nudge published on the topic lands in its recipient's notification center")
    void friendshipNotices_shouldTurnARecordIntoAStoredNotification() {
        publish("{broken", """
                {"type":"REUNION_NUDGE","recipientId":"bob","friendId":"hugo","friendName":"Hugo",\
                "closeness":"INTIMATE","lastSharedAt":null,"lastSharedEventId":null,"monthsApart":null}""");

        await().atMost(Duration.ofSeconds(60)).untilAsserted(() -> {
            List<Notification> page = repository.findPage("bob", null, 10).collectList().block();
            assertThat(page).hasSize(1);
            assertThat(page.getFirst().type()).isEqualTo(NotificationType.REUNION_NUDGE);
            assertThat(page.getFirst().title()).isEqualTo("💭 Et si tu voyais Hugo ?");
            assertThat(page.getFirst().link()).isEqualTo("/friends/hugo?propose=1");
        });
    }
}
