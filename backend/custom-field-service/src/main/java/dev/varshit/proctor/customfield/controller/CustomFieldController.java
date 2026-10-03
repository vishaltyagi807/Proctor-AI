package dev.varshit.proctor.customfield.controller;

import dev.varshit.proctor.common.dto.CustomFieldDefinitionDTO;
import dev.varshit.proctor.common.dto.PageResponse;
import dev.varshit.proctor.common.enums.CustomFieldEntity;
import dev.varshit.proctor.customfield.dto.CreateCustomFieldRequest;
import dev.varshit.proctor.customfield.dto.PatchCustomFieldRequest;
import dev.varshit.proctor.customfield.dto.UpdateCustomFieldRequest;
import dev.varshit.proctor.customfield.service.CustomFieldDefinitionService;
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

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/custom-fields")
public class CustomFieldController {

    private final CustomFieldDefinitionService service;

    public CustomFieldController(CustomFieldDefinitionService service) {
        this.service = service;
    }

    @GetMapping
    public Mono<PageResponse<CustomFieldDefinitionDTO>> query(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size,
            @RequestParam(defaultValue = "sortOrder") String sortBy,
            @RequestParam(defaultValue = "asc") String direction,
            @RequestParam Map<String, String> params
    ) {
        return service.query(params, PageParams.of(page, size, sortBy, direction));
    }

    @GetMapping("/applicable")
    public Flux<CustomFieldDefinitionDTO> applicable(
            @RequestParam CustomFieldEntity entity,
            @RequestParam(required = false) List<UUID> roleId,
            @RequestParam(required = false) List<UUID> departmentId
    ) {
        return service.applicable(entity,
                roleId == null ? new HashSet<>() : new HashSet<>(roleId),
                departmentId == null ? new HashSet<>() : new HashSet<>(departmentId));
    }

    @GetMapping("/{id}")
    public Mono<CustomFieldDefinitionDTO> get(@PathVariable UUID id) {
        return service.get(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Mono<CustomFieldDefinitionDTO> create(@Valid @RequestBody CreateCustomFieldRequest request) {
        return service.create(request);
    }

    @PutMapping("/{id}")
    public Mono<CustomFieldDefinitionDTO> update(@PathVariable UUID id,
                                                 @Valid @RequestBody UpdateCustomFieldRequest request) {
        return service.update(id, request);
    }

    @PatchMapping("/{id}")
    public Mono<CustomFieldDefinitionDTO> patch(@PathVariable UUID id,
                                                @Valid @RequestBody PatchCustomFieldRequest request) {
        return service.patch(id, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public Mono<Void> delete(@PathVariable UUID id) {
        return service.delete(id);
    }
}
