package dev.varshit.proctor.notification.config;

import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

@Component
@Order(-200)
public class PanelSecurityHeadersFilter implements WebFilter {

    private static final String CSP = "default-src 'self'; style-src 'self' 'unsafe-inline'; img-src 'self' data:;"
            + " connect-src 'self'; frame-ancestors 'none'; base-uri 'none'; form-action 'self'";

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        if (exchange.getRequest().getPath().value().startsWith("/notifications/panel")) {
            var headers = exchange.getResponse().getHeaders();
            headers.set("Content-Security-Policy", CSP);
            headers.set("X-Frame-Options", "DENY");
            headers.set("Referrer-Policy", "no-referrer");
            headers.set("Cache-Control", "no-store");
        }
        return chain.filter(exchange);
    }
}
