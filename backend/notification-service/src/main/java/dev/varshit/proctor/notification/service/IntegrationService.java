package dev.varshit.proctor.notification.service;

import dev.varshit.proctor.common.dto.PageResponse;
import dev.varshit.proctor.notification.dto.DeliveryDTO;
import dev.varshit.proctor.notification.dto.DeliveryStatsDTO;
import dev.varshit.proctor.notification.dto.IntegrationDTO;
import dev.varshit.proctor.notification.dto.TestPushRequest;
import dev.varshit.proctor.notification.dto.TestPushResult;
import dev.varshit.proctor.notification.dto.UpdateFcmRequest;
import dev.varshit.proctor.persistence.search.PageParams;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.Map;

public interface IntegrationService {

    Flux<IntegrationDTO> list();

    Mono<IntegrationDTO> updateFcm(UpdateFcmRequest request);

    Mono<Void> clearFcm();

    Mono<TestPushResult> testFcm(TestPushRequest request);

    Mono<PageResponse<DeliveryDTO>> deliveries(Map<String, String> filters, PageParams page);

    Mono<DeliveryStatsDTO> stats();
}
