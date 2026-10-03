package dev.varshit.proctor.notification.controller;

import dev.varshit.proctor.common.dto.PageResponse;
import dev.varshit.proctor.notification.dto.DeliveryDTO;
import dev.varshit.proctor.notification.dto.DeliveryStatsDTO;
import dev.varshit.proctor.notification.dto.IntegrationDTO;
import dev.varshit.proctor.notification.dto.TestPushRequest;
import dev.varshit.proctor.notification.dto.TestPushResult;
import dev.varshit.proctor.notification.dto.UpdateFcmRequest;
import dev.varshit.proctor.notification.service.IntegrationService;
import dev.varshit.proctor.persistence.search.PageParams;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
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

@RestController
@RequestMapping("/notifications/admin")
public class AdminIntegrationController {

    private final IntegrationService service;

    public AdminIntegrationController(IntegrationService service) {
        this.service = service;
    }

    @GetMapping("/integrations")
    public Flux<IntegrationDTO> integrations() {
        return service.list();
    }

    @PutMapping("/integrations/fcm")
    public Mono<IntegrationDTO> updateFcm(@Valid @RequestBody UpdateFcmRequest request) {
        return service.updateFcm(request);
    }

    @DeleteMapping("/integrations/fcm")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public Mono<Void> clearFcm() {
        return service.clearFcm();
    }

    @PostMapping("/integrations/fcm/test")
    public Mono<TestPushResult> testFcm(@Valid @RequestBody(required = false) TestPushRequest request) {
        return service.testFcm(request == null ? new TestPushRequest(null, null, null) : request);
    }

    @GetMapping("/deliveries")
    public Mono<PageResponse<DeliveryDTO>> deliveries(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "25") int size,
            @RequestParam(defaultValue = "createdAt") String sortBy,
            @RequestParam(defaultValue = "desc") String direction,
            @RequestParam Map<String, String> params
    ) {
        return service.deliveries(params, PageParams.of(page, size, sortBy, direction));
    }

    @GetMapping("/stats")
    public Mono<DeliveryStatsDTO> stats() {
        return service.stats();
    }
}
