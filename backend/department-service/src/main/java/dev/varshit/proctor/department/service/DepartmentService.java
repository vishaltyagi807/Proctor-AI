package dev.varshit.proctor.department.service;

import dev.varshit.proctor.common.dto.DepartmentDTO;
import dev.varshit.proctor.common.dto.PageResponse;
import dev.varshit.proctor.department.dto.CreateDepartmentRequest;
import dev.varshit.proctor.department.dto.MemberDTO;
import dev.varshit.proctor.department.dto.PatchDepartmentRequest;
import dev.varshit.proctor.department.dto.UpdateDepartmentRequest;
import dev.varshit.proctor.persistence.search.PageParams;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public interface DepartmentService {

    Mono<PageResponse<DepartmentDTO>> query(Map<String, String> filters, PageParams page);

    Mono<DepartmentDTO> get(UUID id);

    Mono<DepartmentDTO> create(CreateDepartmentRequest request);

    Mono<List<DepartmentDTO>> createBulk(List<CreateDepartmentRequest> requests);

    Mono<DepartmentDTO> update(UUID id, UpdateDepartmentRequest request);

    Mono<DepartmentDTO> patch(UUID id, PatchDepartmentRequest request);

    Mono<Void> delete(UUID id);

    Flux<MemberDTO> members(UUID id);

    Flux<MemberDTO> addMembers(UUID id, Set<UUID> userIds);

    Flux<MemberDTO> removeMembers(UUID id, Set<UUID> userIds);
}
