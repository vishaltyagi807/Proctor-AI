package dev.varshit.proctor.department.repository;

import dev.varshit.proctor.common.dto.DepartmentDTO;
import dev.varshit.proctor.common.dto.PageResponse;
import dev.varshit.proctor.department.dto.MemberDTO;
import dev.varshit.proctor.department.dto.PatchDepartmentRequest;
import dev.varshit.proctor.persistence.search.PageParams;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.Map;
import java.util.Set;
import java.util.UUID;

public interface DepartmentRepository {

    Mono<PageResponse<DepartmentDTO>> search(Map<String, String> filters, PageParams page);

    Mono<DepartmentDTO> findById(UUID id);

    Mono<Long> insert(UUID id, String name, String code, String description, boolean active);

    Mono<Long> update(UUID id, String name, String code, String description, boolean active);

    Mono<Long> patch(UUID id, PatchDepartmentRequest request);

    Mono<Long> delete(UUID id);

    Flux<MemberDTO> findMembers(UUID departmentId);

    Mono<Long> addMembers(UUID departmentId, Set<UUID> userIds);

    Mono<Long> removeMembers(UUID departmentId, Set<UUID> userIds);
}
