package dev.varshit.proctor.gateway.client;

import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

public class ClientAddressFilter implements GlobalFilter, Ordered {

    private final ClientAddressResolver resolver;

    public ClientAddressFilter(ClientAddressResolver resolver) {
        this.resolver = resolver;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String client = resolver.resolve(exchange.getRequest());
        ServerHttpRequest request = exchange.getRequest().mutate()
                .headers(headers -> {
                    headers.remove(ClientAddressResolver.CLIENT_HEADER);
                    if (client != null) {
                        headers.set(ClientAddressResolver.CLIENT_HEADER, client);
                    }
                })
                .build();
        return chain.filter(exchange.mutate().request(request).build());
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE;
    }
}
