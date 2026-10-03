package dev.varshit.proctor.notification.push;

import reactor.core.publisher.Mono;

public interface PushGateway {

    Mono<PushResult> send(FcmCredentials credentials, String deviceToken, PushMessage message);

    Mono<Boolean> verify(FcmCredentials credentials);
}
