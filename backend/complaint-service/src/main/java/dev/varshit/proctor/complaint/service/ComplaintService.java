package dev.varshit.proctor.complaint.service;

import dev.varshit.proctor.common.dto.PageResponse;
import dev.varshit.proctor.complaint.dto.AssignRequest;
import dev.varshit.proctor.complaint.dto.ComplaintDTO;
import dev.varshit.proctor.complaint.dto.CreateComplaintRequest;
import dev.varshit.proctor.complaint.dto.HistoryDTO;
import dev.varshit.proctor.complaint.dto.PatchComplaintRequest;
import dev.varshit.proctor.complaint.dto.StatusRequest;
import dev.varshit.proctor.persistence.search.PageParams;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.Map;
import java.util.UUID;

public interface ComplaintService {

    Mono<PageResponse<ComplaintDTO>> query(Map<String, String> filters, PageParams page);

    Mono<ComplaintDTO> get(UUID id);

    Mono<ComplaintDTO> create(CreateComplaintRequest request);

    Mono<ComplaintDTO> patch(UUID id, PatchComplaintRequest request);

    Mono<ComplaintDTO> changeStatus(UUID id, StatusRequest request);

    Mono<ComplaintDTO> assign(UUID id, AssignRequest request);

    Mono<Void> delete(UUID id);

    Flux<HistoryDTO> history(UUID id);
}
