package com.calendar.notifications.application.dtos;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ReadRequestTest {

    @Test
    @DisplayName("ids alone, a group alone, or all alone is one selector")
    void isExactlyOneSelector_shouldAcceptEachSelectorAlone() {
        assertThat(new ReadRequest(List.of("n1"), null, null).isExactlyOneSelector()).isTrue();
        assertThat(new ReadRequest(null, "c1", false).isExactlyOneSelector()).isTrue();
        assertThat(new ReadRequest(List.of(), null, true).isExactlyOneSelector()).isTrue();
    }

    @Test
    @DisplayName("none, or two at once, is refused")
    void isExactlyOneSelector_shouldRejectNoneOrSeveral() {
        assertThat(new ReadRequest(null, null, null).isExactlyOneSelector()).isFalse();
        assertThat(new ReadRequest(List.of(), " ", false).isExactlyOneSelector()).isFalse();
        assertThat(new ReadRequest(List.of("n1"), "c1", null).isExactlyOneSelector()).isFalse();
        assertThat(new ReadRequest(null, "c1", true).isExactlyOneSelector()).isFalse();
    }
}
