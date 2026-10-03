package dev.varshit.proctor.notification.repository;

import dev.varshit.proctor.common.dto.PageResponse;
import dev.varshit.proctor.notification.dto.DeliveryDTO;
import dev.varshit.proctor.notification.dto.DeliveryStatsDTO;
import dev.varshit.proctor.persistence.search.PageParams;
import reactor.core.publisher.Mono;

import java.util.Map;

public interface DeliveryRepository {

    Mono<PageResponse<DeliveryDTO>> search(Map<String, String> filters, PageParams page);

    Mono<DeliveryStatsDTO> stats();
}
