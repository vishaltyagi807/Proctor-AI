package dev.varshit.proctor.complaint.service;

import dev.varshit.proctor.complaint.dto.ComplaintStatsDTO;
import reactor.core.publisher.Mono;

public interface DashboardService {

    Mono<ComplaintStatsDTO> stats();
}
