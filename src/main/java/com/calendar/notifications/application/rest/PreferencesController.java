package com.calendar.notifications.application.rest;

import com.calendar.notifications.application.dtos.PreferencesDTO;
import com.calendar.notifications.application.mappers.PreferencesRestMapper;
import com.calendar.notifications.domain.services.PushService;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

/** Which categories the caller receives as push. */
@RestController
@RequestMapping("preferences")
public class PreferencesController {

    private static final String BUSINESS_ID = "businessId";

    private final PushService pushService;
    private final PreferencesRestMapper mapper;

    public PreferencesController(PushService pushService, PreferencesRestMapper mapper) {
        this.pushService = pushService;
        this.mapper = mapper;
    }

    @GetMapping
    public Mono<PreferencesDTO> get(@AuthenticationPrincipal Jwt jwt) {
        return pushService.preferences(jwt.getClaimAsString(BUSINESS_ID)).map(mapper::toDto);
    }

    @PutMapping
    public Mono<PreferencesDTO> update(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody PreferencesDTO request) {
        return pushService.updatePreferences(jwt.getClaimAsString(BUSINESS_ID), mapper.toDomain(request))
                .map(mapper::toDto);
    }
}
