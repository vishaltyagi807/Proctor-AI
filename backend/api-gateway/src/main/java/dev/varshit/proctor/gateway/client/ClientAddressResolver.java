package dev.varshit.proctor.gateway.client;

import org.springframework.http.server.reactive.ServerHttpRequest;

import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

public final class ClientAddressResolver {

    public static final String CLIENT_HEADER = "X-Client-IP";
    public static final String FORWARDED_FOR = "X-Forwarded-For";

    private static final Pattern IP_LITERAL = Pattern.compile("[0-9A-Fa-f:.]{2,45}");

    private final int trustedProxies;

    public ClientAddressResolver(int trustedProxies) {
        if (trustedProxies < 0) {
            throw new IllegalArgumentException("app.gateway.trusted-proxies must not be negative");
        }
        this.trustedProxies = trustedProxies;
    }

    public String resolve(ServerHttpRequest request) {
        String remote = remoteAddress(request);
        List<String> hops = new ArrayList<>();
        for (String header : request.getHeaders().getOrEmpty(FORWARDED_FOR)) {
            for (String part : header.split(",")) {
                String hop = part.trim();
                if (!hop.isEmpty()) {
                    hops.add(hop);
                }
            }
        }
        if (remote != null) {
            hops.add(remote);
        }
        if (hops.isEmpty()) {
            return null;
        }
        String candidate = hops.get(Math.max(0, hops.size() - 1 - trustedProxies));
        return IP_LITERAL.matcher(candidate).matches() ? candidate : remote;
    }

    private static String remoteAddress(ServerHttpRequest request) {
        InetSocketAddress address = request.getRemoteAddress();
        if (address == null) {
            return null;
        }
        return address.getAddress() != null ? address.getAddress().getHostAddress() : address.getHostString();
    }
}
