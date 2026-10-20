package com.calendar.notifications.exception;

import org.springframework.web.server.ServerWebExchange;

/**
 * The request id every problem and log line carries: the gateway's {@code X-Request-Id} when the
 * request came through it, so one id follows a request from the edge to this service's logs.
 */
public final class RequestIds {

    /** Header the gateway sets, and this service echoes on its responses and forwards to Wely services. */
    public static final String REQUEST_ID_HEADER = "X-Request-Id";

    /** Exchange attribute, and Reactor Context key, holding the id once {@code RequestIdWebFilter} chose it. */
    public static final String REQUEST_ID_ATTRIBUTE = "wely.requestId";

    private RequestIds() {
    }

    /** The id the filter chose; WebFlux's own request id when the filter did not run (a bare unit test). */
    public static String of(ServerWebExchange exchange) {
        String chosen = exchange.getAttribute(REQUEST_ID_ATTRIBUTE);
        return chosen != null ? chosen : exchange.getRequest().getId();
    }
}
