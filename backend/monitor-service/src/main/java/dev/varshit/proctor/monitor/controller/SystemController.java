package dev.varshit.proctor.monitor.controller;

import dev.varshit.proctor.monitor.dto.SystemOverview;
import dev.varshit.proctor.monitor.repository.MonitorAccessGuard;
import dev.varshit.proctor.monitor.sampler.SystemSampler;
import dev.varshit.proctor.security.CurrentUser;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Duration;

@RestController
@RequestMapping("/system")
public class SystemController {

    private static final Duration HEARTBEAT = Duration.ofSeconds(15);
    private static final Duration PERMISSION_RECHECK = Duration.ofSeconds(30);

    private final SystemSampler sampler;
    private final MonitorAccessGuard guard;

    public SystemController(SystemSampler sampler, MonitorAccessGuard guard) {
        this.sampler = sampler;
        this.guard = guard;
    }

    @GetMapping("/overview")
    public Mono<SystemOverview> overview() {
        return CurrentUser.require()
                .flatMap(guard::requireRead)
                .then(Mono.fromSupplier(sampler::overview));
    }

    @GetMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ServerSentEvent<Object>> stream() {
        return CurrentUser.require().flatMapMany(principal -> guard.requireRead(principal).thenMany(Flux.defer(() -> {
            Mono<Boolean> revoked = Flux.interval(PERMISSION_RECHECK)
                    .concatMap(tick -> guard.allowed(principal).onErrorReturn(true))
                    .filter(allowed -> !allowed)
                    .next();
            Flux<ServerSentEvent<Object>> live = Flux.merge(
                    sampler.updates().map(snapshot -> event("snapshot", snapshot)),
                    Flux.interval(HEARTBEAT).map(tick -> event("heartbeat", "ping")));
            return Flux.concat(Flux.just(event("overview", sampler.overview())), live).takeUntilOther(revoked);
        })));
    }

    private static ServerSentEvent<Object> event(String name, Object data) {
        return ServerSentEvent.builder(data).event(name).build();
    }
}
