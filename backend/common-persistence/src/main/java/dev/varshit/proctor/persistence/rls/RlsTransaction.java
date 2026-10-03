package dev.varshit.proctor.persistence.rls;

import dev.varshit.proctor.security.CurrentUser;
import dev.varshit.proctor.security.UserPrincipal;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.transaction.ReactiveTransactionManager;
import org.springframework.transaction.reactive.TransactionalOperator;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.function.Supplier;

public class RlsTransaction implements SecureTransaction {

    private static final String BIND_IDENTITY =
            "select set_config('auth.uid', :uid, true), set_config('auth.email', :email, true)";

    private final TransactionalOperator operator;
    private final DatabaseClient client;

    public RlsTransaction(ReactiveTransactionManager transactionManager, DatabaseClient client) {
        this.operator = TransactionalOperator.create(transactionManager);
        this.client = client;
    }

    @Override
    public <T> Mono<T> mono(Supplier<Mono<T>> work) {
        return CurrentUser.require().flatMap(principal -> monoAs(principal, work));
    }

    @Override
    public <T> Flux<T> flux(Supplier<Flux<T>> work) {
        return CurrentUser.require().flatMapMany(principal ->
                operator.transactional(bindIdentity(principal).thenMany(Flux.defer(work))));
    }

    @Override
    public <T> Mono<T> monoAs(UserPrincipal principal, Supplier<Mono<T>> work) {
        return operator.transactional(bindIdentity(principal).then(Mono.defer(work)));
    }

    private Mono<Void> bindIdentity(UserPrincipal principal) {
        return client.sql(BIND_IDENTITY)
                .bind("uid", principal.id().toString())
                .bind("email", principal.email())
                .fetch()
                .all()
                .then();
    }
}
