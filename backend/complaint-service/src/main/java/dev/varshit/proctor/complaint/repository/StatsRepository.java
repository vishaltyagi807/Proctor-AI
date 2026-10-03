package dev.varshit.proctor.complaint.repository;

import dev.varshit.proctor.complaint.dto.ComplaintStatsDTO;
import reactor.core.publisher.Mono;

public interface StatsRepository {

    Mono<Boolean> canReadDashboard();

    Mono<ComplaintStatsDTO> complaintStats();
}
