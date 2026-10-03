package dev.varshit.proctor.notification.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.varshit.proctor.common.dto.PageResponse;
import dev.varshit.proctor.common.exception.BadRequestException;
import dev.varshit.proctor.common.exception.ForbiddenException;
import dev.varshit.proctor.notification.crypto.SecretCipher;
import dev.varshit.proctor.notification.dto.DeliveryDTO;
import dev.varshit.proctor.notification.dto.DeliveryStatsDTO;
import dev.varshit.proctor.notification.dto.DeviceTarget;
import dev.varshit.proctor.notification.dto.IntegrationDTO;
import dev.varshit.proctor.notification.dto.IntegrationRow;
import dev.varshit.proctor.notification.dto.TestPushRequest;
import dev.varshit.proctor.notification.dto.TestPushResult;
import dev.varshit.proctor.notification.dto.UpdateFcmRequest;
import dev.varshit.proctor.notification.push.FcmCredentials;
import dev.varshit.proctor.notification.push.FcmCredentialsProvider;
import dev.varshit.proctor.notification.push.PushGateway;
import dev.varshit.proctor.notification.push.PushMessage;
import dev.varshit.proctor.notification.push.PushResult;
import dev.varshit.proctor.notification.repository.DeliveryRepository;
import dev.varshit.proctor.notification.repository.DeviceRepository;
import dev.varshit.proctor.notification.repository.IntegrationRepository;
import dev.varshit.proctor.notification.repository.NotificationRepository;
import dev.varshit.proctor.persistence.rls.SecureTransaction;
import dev.varshit.proctor.persistence.search.PageParams;
import dev.varshit.proctor.security.CurrentUser;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Map;

@Service
public class IntegrationServiceImpl implements IntegrationService {

    private static final String FCM = "fcm";

    private final IntegrationRepository integrations;
    private final DeliveryRepository deliveries;
    private final DeviceRepository devices;
    private final NotificationRepository permissions;
    private final SecureTransaction transaction;
    private final SecretCipher cipher;
    private final ObjectMapper mapper;
    private final PushGateway push;
    private final FcmCredentialsProvider credentialsProvider;

    public IntegrationServiceImpl(IntegrationRepository integrations, DeliveryRepository deliveries,
                                  DeviceRepository devices, NotificationRepository permissions,
                                  SecureTransaction transaction, SecretCipher cipher, ObjectMapper mapper,
                                  PushGateway push, FcmCredentialsProvider credentialsProvider) {
        this.integrations = integrations;
        this.deliveries = deliveries;
        this.devices = devices;
        this.permissions = permissions;
        this.transaction = transaction;
        this.cipher = cipher;
        this.mapper = mapper;
        this.push = push;
        this.credentialsProvider = credentialsProvider;
    }

    @Override
    public Flux<IntegrationDTO> list() {
        return transaction.flux(() -> requireCan("read").thenMany(integrations.findAll().map(IntegrationRow::toDto)));
    }

    @Override
    public Mono<IntegrationDTO> updateFcm(UpdateFcmRequest request) {
        return CurrentUser.require().flatMap(principal -> transaction.monoAs(principal, () ->
                requireCan("update")
                        .then(integrations.find(FCM).map(row -> true).defaultIfEmpty(false))
                        .flatMap(exists -> save(request, exists, principal.id()))
                        .then(integrations.find(FCM))
                        .map(IntegrationRow::toDto)
                        .doOnSuccess(dto -> credentialsProvider.invalidate())));
    }

    @Override
    public Mono<Void> clearFcm() {
        return transaction.mono(() -> requireCan("update")
                .then(integrations.clear(FCM))
                .doOnSuccess(rows -> credentialsProvider.invalidate())
                .then());
    }

    @Override
    public Mono<TestPushResult> testFcm(TestPushRequest request) {
        return transaction.mono(() -> requireCan("update")
                .then(integrations.find(FCM))
                .switchIfEmpty(Mono.error(new BadRequestException("FCM is not configured")))
                .flatMap(row -> {
                    if (row.secretCiphertext() == null) {
                        return Mono.error(new BadRequestException("No service account has been saved"));
                    }
                    FcmCredentials credentials = FcmCredentials.parse(mapper, cipher.decrypt(row.secretCiphertext()));
                    return push.verify(credentials).flatMap(valid -> valid
                            ? sendTest(credentials, request)
                            : Mono.just(new TestPushResult(false, 0, 0, 0,
                            "Google rejected the service account credentials")));
                }));
    }

    @Override
    public Mono<PageResponse<DeliveryDTO>> deliveries(Map<String, String> filters, PageParams page) {
        return transaction.mono(() -> requireCan("read").then(deliveries.search(filters, page)));
    }

    @Override
    public Mono<DeliveryStatsDTO> stats() {
        return transaction.mono(() -> requireCan("read").then(deliveries.stats()));
    }

    private Mono<TestPushResult> sendTest(FcmCredentials credentials, TestPushRequest request) {
        PushMessage message = new PushMessage(
                request.title() == null || request.title().isBlank() ? "Test notification" : request.title(),
                request.body() == null ? "Push notifications are working." : request.body(),
                Map.of("type", "test"), false);
        Flux<String> tokens = request.deviceToken() != null && !request.deviceToken().isBlank()
                ? Flux.just(request.deviceToken().trim())
                : devices.findMyTargets().map(DeviceTarget::token);
        return tokens.collectList().flatMap(list -> Flux.fromIterable(list)
                .concatMap(token -> push.send(credentials, token, message))
                .collectList()
                .map(results -> summarise(list, results)));
    }

    private TestPushResult summarise(List<String> tokens, List<PushResult> results) {
        long sent = results.stream().filter(r -> r.status() == PushResult.Status.SENT).count();
        String detail = tokens.isEmpty()
                ? "Credentials are valid, but you have no registered devices to send to"
                : results.stream().filter(r -> r.status() != PushResult.Status.SENT).map(PushResult::detail)
                .findFirst().orElse("Delivered to FCM");
        return new TestPushResult(true, tokens.size(), (int) sent, (int) (tokens.size() - sent), detail);
    }

    private Mono<Long> save(UpdateFcmRequest request, boolean exists, java.util.UUID userId) {
        String ciphertext = null;
        String config = "{}";
        if (request.serviceAccountJson() != null && !request.serviceAccountJson().isBlank()) {
            FcmCredentials credentials = FcmCredentials.parse(mapper, request.serviceAccountJson());
            ciphertext = cipher.encrypt(request.serviceAccountJson());
            config = toJson(Map.of("project_id", credentials.projectId(), "client_email", credentials.clientEmail()));
        }
        boolean enabled = Boolean.TRUE.equals(request.enabled());
        if (ciphertext == null && !exists) {
            return Mono.error(new BadRequestException("A service account JSON is required the first time"));
        }
        if (ciphertext == null) {
            return integrations.find(FCM).flatMap(row -> {
                if (enabled && row.secretCiphertext() == null) {
                    return Mono.<Long>error(new BadRequestException("Cannot enable FCM without a service account"));
                }
                return integrations.upsert(FCM, enabled, toJson(row.config()), null, userId);
            });
        }
        return integrations.upsert(FCM, enabled, config, ciphertext, userId);
    }

    private Mono<Void> requireCan(String action) {
        return permissions.can("integrations", action)
                .filter(Boolean::booleanValue)
                .switchIfEmpty(Mono.error(new ForbiddenException("You cannot manage notification integrations")))
                .then();
    }

    private String toJson(Object value) {
        try {
            return mapper.writeValueAsString(value == null ? Map.of() : value);
        } catch (JsonProcessingException e) {
            throw new BadRequestException("Invalid configuration");
        }
    }
}
