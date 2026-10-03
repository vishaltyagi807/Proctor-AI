package dev.varshit.proctor.department.controller;

import dev.varshit.proctor.common.dto.DepartmentDTO;
import dev.varshit.proctor.common.dto.PageResponse;
import dev.varshit.proctor.department.dto.CreateDepartmentRequest;
import dev.varshit.proctor.department.dto.MemberDTO;
import dev.varshit.proctor.department.dto.MembersRequest;
import dev.varshit.proctor.department.dto.PatchDepartmentRequest;
import dev.varshit.proctor.department.dto.UpdateDepartmentRequest;
import dev.varshit.proctor.department.service.DepartmentService;
import dev.varshit.proctor.persistence.search.PageParams;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/departments")
public class DepartmentController {

    private final DepartmentService service;

    public DepartmentController(DepartmentService service) {
        this.service = service;
    }

    @GetMapping
    public Mono<PageResponse<DepartmentDTO>> query(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(defaultValue = "createdAt") String sortBy,
            @RequestParam(defaultValue = "desc") String direction,
            @RequestParam Map<String, String> params
    ) {
        return service.query(params, PageParams.of(page, size, sortBy, direction));
    }

    @PostMapping
    public Mono<DepartmentDTO> create(@Valid @RequestBody CreateDepartmentRequest request) {
        return service.create(request);
    }

    @PostMapping("/bulk")
    public Mono<List<DepartmentDTO>> createBulk(@RequestBody List<@Valid CreateDepartmentRequest> requests) {
        return service.createBulk(requests);
    }

    @GetMapping("/{id}")
    public Mono<DepartmentDTO> get(@PathVariable UUID id) {
        return service.get(id);
    }

    @PutMapping("/{id}")
    public Mono<DepartmentDTO> update(@PathVariable UUID id, @Valid @RequestBody UpdateDepartmentRequest request) {
        return service.update(id, request);
    }

    @PatchMapping("/{id}")
    public Mono<DepartmentDTO> patch(@PathVariable UUID id, @Valid @RequestBody PatchDepartmentRequest request) {
        return service.patch(id, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public Mono<Void> delete(@PathVariable UUID id) {
        return service.delete(id);
    }

    @GetMapping("/{id}/members")
    public Flux<MemberDTO> members(@PathVariable UUID id) {
        return service.members(id);
    }

    @PutMapping("/{id}/members")
    public Flux<MemberDTO> addMembers(@PathVariable UUID id, @Valid @RequestBody MembersRequest request) {
        return service.addMembers(id, request.userIds());
    }

    @DeleteMapping("/{id}/members")
    public Flux<MemberDTO> removeMembers(@PathVariable UUID id, @Valid @RequestBody MembersRequest request) {
        return service.removeMembers(id, request.userIds());
    }
}
