package com.calendar.notifications.domain.services;

import com.calendar.notifications.domain.models.EventMembership;
import com.calendar.notifications.domain.ports.EventMembers;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.time.Duration;
import java.util.List;

import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EventMembershipServiceTest {

    private static final EventMembership MEMBERSHIP = new EventMembership("e1", "Bowling", List.of("a", "b"), 2);

    @Mock
    private EventMembers eventMembers;
    @InjectMocks
    private EventMembershipService service;

    @Test
    @DisplayName("applying says whether the projection took it")
    void apply_shouldReportWhetherTheMembershipWasStored() {
        when(eventMembers.apply(MEMBERSHIP)).thenReturn(Mono.just(false));

        StepVerifier.create(service.apply(MEMBERSHIP)).expectNext(false).expectComplete().verify(Duration.ofSeconds(10));
    }

    @Test
    @DisplayName("a known event's members are found; an unknown one is empty")
    void find_shouldReadTheProjection() {
        when(eventMembers.find("e1")).thenReturn(Mono.just(MEMBERSHIP));
        when(eventMembers.find("e2")).thenReturn(Mono.empty());

        StepVerifier.create(service.find("e1")).expectNext(MEMBERSHIP).expectComplete().verify(Duration.ofSeconds(10));
        StepVerifier.create(service.find("e2")).expectComplete().verify(Duration.ofSeconds(10));
    }
}
