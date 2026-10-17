package com.calendar.notifications.application.rest;

import com.calendar.notifications.application.dtos.ApnsDeviceRequest;
import com.calendar.notifications.application.mappers.ApnsDeviceRestMapper;
import com.calendar.notifications.domain.services.PushService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

/** The caller's iOS devices for native push through APNs. */
@RestController
@RequestMapping("push/apns-devices")
public class ApnsDeviceController {

    private static final String BUSINESS_ID = "businessId";

    private final PushService pushService;
    private final ApnsDeviceRestMapper mapper;

    public ApnsDeviceController(PushService pushService, ApnsDeviceRestMapper mapper) {
        this.pushService = pushService;
        this.mapper = mapper;
    }

    /** Registers this iOS device for the caller; the same token registered again is updated, not duplicated. */
    @PutMapping
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public Mono<Void> register(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody ApnsDeviceRequest request) {
        return pushService.registerDevice(jwt.getClaimAsString(BUSINESS_ID), mapper.toDevice(request));
    }

    /** 204 whether or not the token was the caller's: someone else's is left alone. */
    @DeleteMapping
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public Mono<Void> unregister(@AuthenticationPrincipal Jwt jwt, @RequestParam String token) {
        return pushService.unregisterDevice(jwt.getClaimAsString(BUSINESS_ID), token);
    }
}
