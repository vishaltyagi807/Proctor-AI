package dev.varshit.proctor.complaint.service;

import dev.varshit.proctor.common.exception.ForbiddenException;
import dev.varshit.proctor.complaint.dto.ComplaintStatsDTO;
import dev.varshit.proctor.complaint.repository.StatsRepository;
import dev.varshit.proctor.persistence.rls.SecureTransaction;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

@Service
public class DashboardServiceImpl implements DashboardService {

    private final StatsRepository stats;
    private final SecureTransaction transaction;

    public DashboardServiceImpl(StatsRepository stats, SecureTransaction transaction) {
        this.stats = stats;
        this.transaction = transaction;
    }

    @Override
    public Mono<ComplaintStatsDTO> stats() {
        return transaction.mono(() -> stats.canReadDashboard()
                .filter(Boolean::booleanValue)
                .switchIfEmpty(Mono.error(new ForbiddenException("You cannot view the dashboard")))
                .then(stats.complaintStats()));
    }
}
