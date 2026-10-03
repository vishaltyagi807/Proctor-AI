package dev.varshit.proctor.complaint.repository;

import dev.varshit.proctor.complaint.dto.ComplaintStatsDTO;
import dev.varshit.proctor.persistence.sql.SqlGateway;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Mono;

import java.util.Map;

@Repository
public class SqlStatsRepository implements StatsRepository {

    private final SqlGateway gateway;

    public SqlStatsRepository(SqlGateway gateway) {
        this.gateway = gateway;
    }

    @Override
    public Mono<Boolean> canReadDashboard() {
        return gateway.queryLong("select case when can_access('dashboard', 'read') then 1 else 0 end", Map.of())
                .map(value -> value == 1L);
    }

    @Override
    public Mono<ComplaintStatsDTO> complaintStats() {
        return gateway.queryOne(
                "select count(*) as total,"
                        + " count(*) filter (where status = 'pending') as pending,"
                        + " count(*) filter (where status = 'reviewed') as reviewed,"
                        + " count(*) filter (where status = 'resolved') as resolved,"
                        + " count(*) filter (where status = 'rejected') as rejected,"
                        + " count(*) filter (where assigned_to is null and status in ('pending', 'reviewed')) as unassigned,"
                        + " count(*) filter (where priority = 'low') as low,"
                        + " count(*) filter (where priority = 'medium') as medium,"
                        + " count(*) filter (where priority = 'high') as high,"
                        + " count(*) filter (where priority = 'urgent') as urgent,"
                        + " count(*) filter (where status in ('pending', 'reviewed')) as open,"
                        + " count(*) filter (where assigned_to = uid() and status in ('pending', 'reviewed')) as assigned_to_me,"
                        + " count(*) filter (where raised_by = uid() or student_id = uid()) as mine,"
                        + " count(*) filter (where status in ('pending', 'reviewed') and created_at < now() - interval '7 days') as overdue,"
                        + " count(*) filter (where status in ('pending', 'reviewed') and priority in ('high', 'urgent')) as critical_open,"
                        + " count(*) filter (where created_at >= now() - interval '7 days') as created_last7_days,"
                        + " count(*) filter (where status = 'resolved' and resolved_at >= now() - interval '7 days') as resolved_last7_days,"
                        + " round((avg(extract(epoch from resolved_at - created_at) / 3600)"
                        + " filter (where status = 'resolved' and resolved_at is not null))::numeric, 1)::float8 as avg_resolution_hours,"
                        + " (select coalesce(jsonb_agg(jsonb_build_object('day', d.day, 'created', coalesce(cr.n, 0),"
                        + " 'resolved', coalesce(rs.n, 0)) order by d.day), '[]'::jsonb)"
                        + " from (select generate_series(current_date - 13, current_date, interval '1 day')::date as day) d"
                        + " left join (select created_at::date as day, count(*) as n from complaints"
                        + " where created_at >= current_date - 13 group by 1) cr using (day)"
                        + " left join (select resolved_at::date as day, count(*) as n from complaints"
                        + " where status = 'resolved' and resolved_at >= current_date - 13 group by 1) rs using (day)) as trend,"
                        + " (select coalesce(jsonb_agg(t order by t.open desc, t.total desc), '[]'::jsonb)"
                        + " from (select coalesce(department_name(department_id), 'No department') as name, count(*) as total,"
                        + " count(*) filter (where status in ('pending', 'reviewed')) as open"
                        + " from complaints group by department_id order by 3 desc, 2 desc limit 8) t) as departments"
                        + " from complaints",
                Map.of(), ComplaintStatsDTO.class);
    }
}
