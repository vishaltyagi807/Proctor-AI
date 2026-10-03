package dev.varshit.proctor.complaint.controller;

import dev.varshit.proctor.common.dto.PageResponse;
import dev.varshit.proctor.complaint.dto.AssignRequest;
import dev.varshit.proctor.complaint.dto.ComplaintDTO;
import dev.varshit.proctor.complaint.dto.ComplaintStatsDTO;
import dev.varshit.proctor.complaint.dto.CreateComplaintRequest;
import dev.varshit.proctor.complaint.dto.HistoryDTO;
import dev.varshit.proctor.complaint.dto.PatchComplaintRequest;
import dev.varshit.proctor.complaint.dto.StatusRequest;
import dev.varshit.proctor.complaint.service.ComplaintService;
import dev.varshit.proctor.complaint.service.DashboardService;
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

import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/complaints")
public class ComplaintController {

    private final ComplaintService service;
    private final DashboardService dashboard;

    public ComplaintController(ComplaintService service, DashboardService dashboard) {
        this.service = service;
        this.dashboard = dashboard;
    }

    @GetMapping
    public Mono<PageResponse<ComplaintDTO>> query(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(defaultValue = "createdAt") String sortBy,
            @RequestParam(defaultValue = "desc") String direction,
            @RequestParam Map<String, String> params
    ) {
        return service.query(params, PageParams.of(page, size, sortBy, direction));
    }

    @GetMapping("/stats")
    public Mono<ComplaintStatsDTO> stats() {
        return dashboard.stats();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Mono<ComplaintDTO> create(@Valid @RequestBody CreateComplaintRequest request) {
        return service.create(request);
    }

    @GetMapping("/{id}")
    public Mono<ComplaintDTO> get(@PathVariable UUID id) {
        return service.get(id);
    }

    @PatchMapping("/{id}")
    public Mono<ComplaintDTO> patch(@PathVariable UUID id, @Valid @RequestBody PatchComplaintRequest request) {
        return service.patch(id, request);
    }

    @PutMapping("/{id}/status")
    public Mono<ComplaintDTO> changeStatus(@PathVariable UUID id, @Valid @RequestBody StatusRequest request) {
        return service.changeStatus(id, request);
    }

    @PutMapping("/{id}/assignee")
    public Mono<ComplaintDTO> assign(@PathVariable UUID id, @Valid @RequestBody AssignRequest request) {
        return service.assign(id, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public Mono<Void> delete(@PathVariable UUID id) {
        return service.delete(id);
    }

    @GetMapping("/{id}/history")
    public Flux<HistoryDTO> history(@PathVariable UUID id) {
        return service.history(id);
    }
}
