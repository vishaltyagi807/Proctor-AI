package dev.varshit.proctor.monitor.repository;

import dev.varshit.proctor.common.exception.ForbiddenException;
import dev.varshit.proctor.persistence.rls.SecureTransaction;
import dev.varshit.proctor.persistence.sql.SqlGateway;
import dev.varshit.proctor.security.UserPrincipal;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Mono;

import java.util.Map;

@Repository
public class MonitorAccessGuard {

    private record AccessCheck(boolean allowed) {
    }

    private final SqlGateway gateway;
    private final SecureTransaction transaction;

    public MonitorAccessGuard(SqlGateway gateway, SecureTransaction transaction) {
        this.gateway = gateway;
        this.transaction = transaction;
    }

    public Mono<Boolean> allowed(UserPrincipal principal) {
        return transaction.monoAs(principal, () -> gateway.queryOne(
                        "select holds_permission('system_monitor'::entity, 'read'::permission_action) as allowed",
                        Map.of(), AccessCheck.class))
                .map(AccessCheck::allowed)
                .defaultIfEmpty(false);
    }

    public Mono<Void> requireRead(UserPrincipal principal) {
        return allowed(principal).flatMap(allowed -> allowed
                ? Mono.<Void>empty()
                : Mono.error(new ForbiddenException("You do not have permission to view system resources")));
    }
}
