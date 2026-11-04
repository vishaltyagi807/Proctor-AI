package dev.varshit.proctor.gateway.client;

import org.junit.jupiter.api.Test;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;

import java.net.InetSocketAddress;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ClientAddressResolverTest {

    private static final InetSocketAddress NGINX = new InetSocketAddress("172.18.0.5", 41000);

    @Test
    void withoutProxiesTheRemoteAddressIsTheClient() {
        MockServerHttpRequest request = MockServerHttpRequest.get("/")
                .header("X-Forwarded-For", "6.6.6.6").remoteAddress(NGINX).build();

        assertThat(new ClientAddressResolver(0).resolve(request)).isEqualTo("172.18.0.5");
    }

    @Test
    void productionChainPicksTheAddressCaddyRecorded() {
        MockServerHttpRequest request = MockServerHttpRequest.get("/")
                .header("X-Forwarded-For", "198.51.100.7, 172.18.0.9").remoteAddress(NGINX).build();

        assertThat(new ClientAddressResolver(2).resolve(request)).isEqualTo("198.51.100.7");
    }

    @Test
    void spoofedLeadingEntriesAreIgnored() {
        MockServerHttpRequest request = MockServerHttpRequest.get("/")
                .header("X-Forwarded-For", "6.6.6.6, 198.51.100.7, 172.18.0.9").remoteAddress(NGINX).build();

        assertThat(new ClientAddressResolver(2).resolve(request)).isEqualTo("198.51.100.7");
    }

    @Test
    void multipleForwardedHeadersAreJoinedInOrder() {
        MockServerHttpRequest request = MockServerHttpRequest.get("/")
                .header("X-Forwarded-For", "6.6.6.6")
                .header("X-Forwarded-For", "198.51.100.7")
                .remoteAddress(NGINX).build();

        assertThat(new ClientAddressResolver(1).resolve(request)).isEqualTo("198.51.100.7");
    }

    @Test
    void shorterChainThanConfiguredFallsBackToTheFirstHop() {
        MockServerHttpRequest request = MockServerHttpRequest.get("/").remoteAddress(NGINX).build();

        assertThat(new ClientAddressResolver(2).resolve(request)).isEqualTo("172.18.0.5");
    }

    @Test
    void nonAddressValuesFallBackToTheRemoteAddress() {
        MockServerHttpRequest request = MockServerHttpRequest.get("/")
                .header("X-Forwarded-For", "<script>, 172.18.0.9").remoteAddress(NGINX).build();

        assertThat(new ClientAddressResolver(2).resolve(request)).isEqualTo("172.18.0.5");
    }

    @Test
    void ipv6AddressesAreAccepted() {
        MockServerHttpRequest request = MockServerHttpRequest.get("/")
                .header("X-Forwarded-For", "2001:db8::1").remoteAddress(NGINX).build();

        assertThat(new ClientAddressResolver(1).resolve(request)).isEqualTo("2001:db8::1");
    }

    @Test
    void negativeProxyCountIsRejected() {
        assertThatThrownBy(() -> new ClientAddressResolver(-1)).isInstanceOf(IllegalArgumentException.class);
    }
}
