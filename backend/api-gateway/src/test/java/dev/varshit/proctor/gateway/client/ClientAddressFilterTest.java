package dev.varshit.proctor.gateway.client;

import org.junit.jupiter.api.Test;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.net.InetSocketAddress;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class ClientAddressFilterTest {

    @Test
    void clientSuppliedHeaderIsReplacedWithTheResolvedAddress() {
        MockServerHttpRequest request = MockServerHttpRequest.get("/auth/login")
                .header(ClientAddressResolver.CLIENT_HEADER, "6.6.6.6")
                .header("X-Forwarded-For", "198.51.100.7")
                .remoteAddress(new InetSocketAddress("172.18.0.5", 41000))
                .build();
        AtomicReference<ServerWebExchange> forwarded = new AtomicReference<>();

        new ClientAddressFilter(new ClientAddressResolver(1))
                .filter(MockServerWebExchange.from(request), exchange -> {
                    forwarded.set(exchange);
                    return Mono.empty();
                })
                .block();

        assertThat(forwarded.get().getRequest().getHeaders().get(ClientAddressResolver.CLIENT_HEADER))
                .containsExactly("198.51.100.7");
    }
}
