package dev.varshit.proctor.complaint.dto;

import java.time.LocalDate;
import java.util.List;

public record ComplaintStatsDTO(
        long total,
        long pending,
        long reviewed,
        long resolved,
        long rejected,
        long unassigned,
        long low,
        long medium,
        long high,
        long urgent,
        long open,
        long assignedToMe,
        long mine,
        long overdue,
        long criticalOpen,
        long createdLast7Days,
        long resolvedLast7Days,
        Double avgResolutionHours,
        List<TrendPoint> trend,
        List<DepartmentLoad> departments
) {

    public record TrendPoint(LocalDate day, long created, long resolved) {
    }

    public record DepartmentLoad(String name, long total, long open) {
    }
}
