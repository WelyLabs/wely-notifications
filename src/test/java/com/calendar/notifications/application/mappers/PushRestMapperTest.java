package com.calendar.notifications.application.mappers;

import com.calendar.notifications.application.dtos.PublicKeyDTO;
import com.calendar.notifications.application.dtos.PushSubscriptionRequest;
import com.calendar.notifications.domain.models.PushSubscription;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;

import static org.assertj.core.api.Assertions.assertThat;

class PushRestMapperTest {

    private final PushRestMapper mapper = Mappers.getMapper(PushRestMapper.class);

    @Test
    @DisplayName("a browser subscription maps its keys, and leaves owner and time unset")
    void toSubscription_shouldFlattenTheKeys() {
        PushSubscriptionRequest request = new PushSubscriptionRequest("https://push/1",
                new PushSubscriptionRequest.Keys("p256", "auth"), "Phone");

        assertThat(mapper.toSubscription(request))
                .isEqualTo(new PushSubscription(null, "https://push/1", "p256", "auth", "Phone", null));
        assertThat(mapper.toSubscription(null)).isNull();
    }

    @Test
    @DisplayName("a request without keys maps none")
    void toSubscription_shouldTolerateMissingKeys() {
        assertThat(mapper.toSubscription(new PushSubscriptionRequest("https://push/1", null, null)).p256dh()).isNull();
    }

    @Test
    @DisplayName("the public key goes out wrapped")
    void toPublicKey_shouldWrapTheKey() {
        assertThat(mapper.toPublicKey("BPub")).isEqualTo(new PublicKeyDTO("BPub"));
        assertThat(mapper.toPublicKey(null)).isNull();
    }
}
