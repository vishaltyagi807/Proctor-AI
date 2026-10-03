package dev.varshit.proctor.notification.controller;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

@RestController
public class PanelController {

    @GetMapping("/notifications/panel")
    public Mono<Void> panel(ServerWebExchange exchange) {
        exchange.getResponse().setStatusCode(HttpStatus.FOUND);
        exchange.getResponse().getHeaders().set(HttpHeaders.LOCATION, "/notifications/panel/index.html");
        return exchange.getResponse().setComplete();
    }
}
