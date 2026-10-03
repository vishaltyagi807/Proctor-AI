package dev.varshit.proctor.faceservice.repository;

import dev.varshit.proctor.common.exception.ForbiddenException;
import dev.varshit.proctor.persistence.rls.SecureTransaction;
import dev.varshit.proctor.persistence.sql.Params;
import dev.varshit.proctor.persistence.sql.SqlGateway;
import dev.varshit.proctor.security.UserPrincipal;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Mono;

import java.util.Map;
import java.util.UUID;

@Repository
public class FaceAccessGuard {

    public static final String READ = "read";
    public static final String WRITE = "write";
    public static final String UPDATE = "update";
    public static final String DELETE = "delete";

    private record AccessCheck(boolean allowed) {
    }

    private record ManageCheck(boolean permitted, boolean manageable) {
    }

    private final SqlGateway gateway;
    private final SecureTransaction transaction;

    public FaceAccessGuard(SqlGateway gateway, SecureTransaction transaction) {
        this.gateway = gateway;
        this.transaction = transaction;
    }

    public Mono<Void> requireRecognize(boolean live) {
        return transaction.mono(() -> holds(live ? "face_live_recognition" : "face_recognition", READ))
                .flatMap(allowed -> allowOr(allowed, live
                        ? "You do not have permission to use live camera recognition"
                        : "You do not have permission to recognize faces in uploaded photos"));
    }

    public Mono<Void> requireAudit() {
        return transaction.mono(() -> holds("face_audit", READ))
                .flatMap(allowed -> allowOr(allowed, "You do not have permission to view face recognition activity"));
    }

    public Mono<Void> requireUnlock(UserPrincipal actor, UUID targetUserId) {
        return transaction.monoAs(actor, () -> gateway.queryOne(
                        "select can_access('face_enroll_lock'::entity, 'update'::permission_action,"
                                + " jsonb_build_object('id', :userId)) as permitted,"
                                + " can_manage_user(:userId, 'update'::permission_action) as manageable",
                        Params.create().with("userId", targetUserId).build(),
                        ManageCheck.class))
                .flatMap(check -> {
                    if (!check.permitted()) {
                        return Mono.error(new ForbiddenException("You do not have permission to reset this user's enrollment lock"));
                    }
                    if (!check.manageable()) {
                        return Mono.error(new ForbiddenException(
                                "You cannot manage the face data of this user because of their rank or role"));
                    }
                    return Mono.<Void>empty();
                });
    }

    public Mono<Void> requireListEnrollments() {
        return transaction.mono(() -> holds("face_enrollments", READ))
                .flatMap(allowed -> allowOr(allowed, "You do not have permission to view face enrollments"));
    }

    public Mono<Void> requireImport(UserPrincipal actor, String action) {
        return transaction.monoAs(actor, () -> holds("face_import", action))
                .flatMap(allowed -> allowOr(allowed, READ.equals(action)
                        ? "You do not have permission to download the face import template"
                        : "You do not have permission to bulk import faces"));
    }

    public Mono<Void> requireManage(UserPrincipal actor, String action, UUID targetUserId) {
        return transaction.monoAs(actor, () -> gateway.queryOne(
                        "select can_access('face_enrollments'::entity, :action::permission_action,"
                                + " jsonb_build_object('id', :userId)) as permitted,"
                                + " can_manage_user(:userId, :action::permission_action) as manageable",
                        Params.create().with("action", action).with("userId", targetUserId).build(),
                        ManageCheck.class))
                .flatMap(check -> {
                    if (!check.permitted()) {
                        return Mono.error(new ForbiddenException(deniedMessage(action)));
                    }
                    if (!check.manageable()) {
                        return Mono.error(new ForbiddenException(
                                "You cannot manage the face data of this user because of their rank or role"));
                    }
                    return Mono.<Void>empty();
                });
    }

    public Mono<Boolean> isPrivileged(UserPrincipal actor, String action, UUID targetUserId) {
        return transaction.monoAs(actor, () -> gateway.queryOne(
                        "select face_privileged(:action::permission_action, :userId) as allowed",
                        Params.create().with("action", action).with("userId", targetUserId).build(),
                        AccessCheck.class))
                .map(AccessCheck::allowed);
    }

    private Mono<Boolean> holds(String entity, String action) {
        return gateway.queryOne(
                        "select holds_permission(:entity::entity, :action::permission_action) as allowed",
                        Map.of("entity", entity, "action", action),
                        AccessCheck.class)
                .map(AccessCheck::allowed);
    }

    private Mono<Void> allowOr(boolean allowed, String message) {
        return allowed ? Mono.empty() : Mono.error(new ForbiddenException(message));
    }

    private String deniedMessage(String action) {
        return switch (action) {
            case WRITE -> "You do not have permission to enroll this user's face";
            case UPDATE -> "You do not have permission to replace this user's face enrollment";
            case DELETE -> "You do not have permission to remove this user's face enrollment";
            default -> "You do not have permission to view this user's face enrollment";
        };
    }
}
