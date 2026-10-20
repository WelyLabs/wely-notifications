package com.calendar.notifications.configuration;

import com.calendar.notifications.exception.RequestIds;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;
import reactor.util.context.Context;

import java.util.regex.Pattern;

/**
 * Chooses the request id before anything else runs: the gateway's {@code X-Request-Id} when it
 * looks like one, otherwise WebFlux's own id. It is stored as an exchange attribute (for problems
 * and logs) and in the Reactor Context (for outgoing calls), and echoed on the response.
 *
 * <p>The header is only trusted in shape: it reaches logs and responses, so anything that is not
 * a short token — a forged log line, a huge value — falls back to the local id.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class RequestIdWebFilter implements WebFilter {

    static final Pattern VALID_ID = Pattern.compile("^[A-Za-z0-9-]{8,64}$");

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        String id = chooseId(exchange);
        exchange.getAttributes().put(RequestIds.REQUEST_ID_ATTRIBUTE, id);
        exchange.getResponse().getHeaders().set(RequestIds.REQUEST_ID_HEADER, id);
        return chain.filter(exchange)
                .contextWrite(Context.of(RequestIds.REQUEST_ID_ATTRIBUTE, id));
    }

    private static String chooseId(ServerWebExchange exchange) {
        String received = exchange.getRequest().getHeaders().getFirst(RequestIds.REQUEST_ID_HEADER);
        return received != null && VALID_ID.matcher(received).matches() ? received : exchange.getRequest().getId();
    }
}
