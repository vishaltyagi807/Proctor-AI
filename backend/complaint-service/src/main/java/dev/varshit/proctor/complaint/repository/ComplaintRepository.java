package dev.varshit.proctor.complaint.repository;

import dev.varshit.proctor.common.dto.PageResponse;
import dev.varshit.proctor.complaint.dto.ComplaintDTO;
import dev.varshit.proctor.complaint.dto.CreateComplaintRequest;
import dev.varshit.proctor.complaint.dto.PatchComplaintRequest;
import dev.varshit.proctor.persistence.search.PageParams;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Map;
import java.util.UUID;

public interface ComplaintRepository {

    Mono<PageResponse<ComplaintDTO>> search(Map<String, String> filters, PageParams page);

    Mono<ComplaintDTO> findById(UUID id);

    Mono<Long> insert(UUID id, UUID studentId, CreateComplaintRequest request);

    Mono<Long> insertSubjects(UUID id, List<UUID> userIds);

    Mono<Long> patch(UUID id, PatchComplaintRequest request);

    Mono<Long> setStatusNote(String note);

    Mono<Long> delete(UUID id);

    Mono<dev.varshit.proctor.complaint.dto.ComplaintParties> parties(UUID id);
}
