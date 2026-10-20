package com.calendar.notifications.infrastructure.messaging.adapters;

import com.calendar.notifications.domain.models.EventMembership;
import com.calendar.notifications.domain.services.EventMembershipService;
import com.calendar.notifications.exception.GlobalErrorHandler;
import com.calendar.notifications.exception.TechnicalErrorCode;
import com.calendar.notifications.exception.TechnicalErrorCounter;
import com.calendar.notifications.exception.TechnicalException;
import com.calendar.notifications.infrastructure.messaging.mappers.EventMembershipMapper;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mapstruct.factory.Mappers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;
import tools.jackson.databind.json.JsonMapper;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EventGroupsConsumerTest {

    private final SimpleMeterRegistry registry = new SimpleMeterRegistry();
    private final TechnicalErrorCounter technicalErrors = new TechnicalErrorCounter(registry);

    @Mock
    private EventMembershipService membershipService;

    private EventGroupsConsumer consumer;

    @BeforeEach
    void setUp() {
        consumer = new EventGroupsConsumer(membershipService, Mappers.getMapper(EventMembershipMapper.class),
                new RecordReader(JsonMapper.builder().build(), technicalErrors), technicalErrors);
    }

    private static org.springframework.messaging.Message<byte[]> group(String eventId, long version, boolean deleted) {
        return CallEventsConsumerTest.record("""
                {"eventId":"%s","title":"Bowling","organizerId":"olivia","memberIds":["olivia","bob"],
                 "version":%d,"deleted":%s}""".formatted(eventId, version, deleted));
    }

    @Test
    @DisplayName("each record updates the members projection")
    void eventGroups_shouldApplyTheMembership() {
        when(membershipService.apply(any())).thenReturn(Mono.just(true));

        StepVerifier.create(consumer.eventGroups().apply(Flux.just(group("e1", 3, false)))).expectComplete().verify(Duration.ofSeconds(10));

        verify(membershipService, timeout(2000))
                .apply(new EventMembership("e1", "Bowling", List.of("olivia", "bob"), 3));
    }

    @Test
    @DisplayName("a deleted event keeps its version with nobody in it and no title")
    void eventGroups_shouldEmptyADeletedEvent() {
        when(membershipService.apply(any())).thenReturn(Mono.just(true));

        StepVerifier.create(consumer.eventGroups().apply(Flux.just(group("e1", 4, true)))).expectComplete().verify(Duration.ofSeconds(10));

        verify(membershipService, timeout(2000)).apply(new EventMembership("e1", null, List.of(), 4));
    }

    @Test
    @DisplayName("an unreadable record or one without an event is dropped, the next still applied")
    void eventGroups_shouldDropUnreadableRecords() {
        when(membershipService.apply(any())).thenReturn(Mono.just(true));

        StepVerifier.create(consumer.eventGroups().apply(Flux.just(CallEventsConsumerTest.record("nope"),
                CallEventsConsumerTest.record("{\"title\":\"x\",\"version\":1,\"deleted\":false}"), group("e2", 1, false)))).expectComplete().verify(Duration.ofSeconds(10));

        verify(membershipService, timeout(2000)).apply(argThat(membership -> "e2".equals(membership.eventId())));
        verify(membershipService, never()).apply(argThat(membership -> membership.eventId() == null));

        assertThat(technicalFailures(GlobalErrorHandler.UNEXPECTED_FAILURE_CODE)).isEqualTo(1.0);
        assertThat(technicalFailures("NTF-TEC-001")).isEqualTo(0.0);
    }

    @Test
    @DisplayName("a failing write is retried, then dropped without stopping the stream")
    void eventGroups_shouldRetryThenKeepConsuming() {
        AtomicInteger attempts = new AtomicInteger();
        when(membershipService.apply(argThat(membership -> membership != null && "e1".equals(membership.eventId()))))
                .thenReturn(Mono.defer(() -> {
                    attempts.incrementAndGet();
                    return Mono.error(new TechnicalException(TechnicalErrorCode.STORE_UNAVAILABLE));
                }));
        when(membershipService.apply(argThat(membership -> membership != null && "e2".equals(membership.eventId()))))
                .thenReturn(Mono.just(true));

        StepVerifier.create(consumer.eventGroups().apply(Flux.just(group("e1", 1, false), group("e2", 1, false)))).expectComplete().verify(Duration.ofSeconds(10));

        verify(membershipService, timeout(20_000)).apply(argThat(membership -> membership != null
                && "e2".equals(membership.eventId())));
        assertThat(attempts.get()).isEqualTo(4);

        assertThat(technicalFailures(GlobalErrorHandler.UNEXPECTED_FAILURE_CODE)).isEqualTo(0.0);
        assertThat(technicalFailures("NTF-TEC-001")).isEqualTo(1.0);
    }

    @Test
    @DisplayName("nothing is read until the binder subscribes to the function's result")
    void eventGroups_shouldDoNothingUntilTheBinderSubscribes() {
        consumer.eventGroups().apply(Flux.just(group("e1", 3, false)));

        verifyNoInteractions(membershipService);
    }

    private double technicalFailures(String code) {
        Counter counter = registry.find(GlobalErrorHandler.TECHNICAL_ERRORS_METRIC)
                .tag(GlobalErrorHandler.CODE_TAG, code)
                .tag(GlobalErrorHandler.SOURCE_TAG, TechnicalErrorCounter.SOURCE_KAFKA)
                .counter();
        return counter == null ? 0 : counter.count();
    }
}
