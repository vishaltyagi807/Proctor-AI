package dev.varshit.proctor.gateway.client;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.filter.ratelimit.KeyResolver;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import reactor.core.publisher.Mono;

@Configuration(proxyBeanMethods = false)
public class ClientAddressConfiguration {

    private static final String UNKNOWN_CLIENT = "unknown";

    @Bean
    public ClientAddressResolver clientAddressResolver(@Value("${app.gateway.trusted-proxies:0}") int trustedProxies) {
        return new ClientAddressResolver(trustedProxies);
    }

    @Bean
    public ClientAddressFilter clientAddressFilter(ClientAddressResolver resolver) {
        return new ClientAddressFilter(resolver);
    }

    @Bean
    public KeyResolver clientAddressKeyResolver(ClientAddressResolver resolver) {
        return exchange -> Mono.justOrEmpty(resolver.resolve(exchange.getRequest())).defaultIfEmpty(UNKNOWN_CLIENT);
    }
}
