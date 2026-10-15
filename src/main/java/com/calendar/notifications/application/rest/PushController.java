package com.calendar.notifications.application.rest;

import com.calendar.notifications.application.dtos.PublicKeyDTO;
import com.calendar.notifications.application.dtos.PushSubscriptionRequest;
import com.calendar.notifications.application.mappers.PushRestMapper;
import com.calendar.notifications.domain.services.PushService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

/** The caller's devices for Web Push. */
@RestController
@RequestMapping("push")
public class PushController {

    private static final String BUSINESS_ID = "businessId";

    private final PushService pushService;
    private final PushRestMapper mapper;

    public PushController(PushService pushService, PushRestMapper mapper) {
        this.pushService = pushService;
        this.mapper = mapper;
    }

    @GetMapping("/public-key")
    public PublicKeyDTO publicKey() {
        return mapper.toPublicKey(pushService.publicKey());
    }

    /** Registers this device for the caller; the same endpoint registered again is updated, not duplicated. */
    @PutMapping("/subscriptions")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public Mono<Void> subscribe(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody PushSubscriptionRequest request) {
        return pushService.subscribe(jwt.getClaimAsString(BUSINESS_ID), mapper.toSubscription(request));
    }

    /** 204 whether or not the endpoint was the caller's: someone else's is left alone. */
    @DeleteMapping("/subscriptions")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public Mono<Void> unsubscribe(@AuthenticationPrincipal Jwt jwt, @RequestParam String endpoint) {
        return pushService.unsubscribe(jwt.getClaimAsString(BUSINESS_ID), endpoint);
    }
}
