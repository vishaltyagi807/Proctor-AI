create function system_database_stats() returns jsonb
    language sql
    stable
    security definer
    set search_path = public, pg_catalog
as
$$
select jsonb_build_object(
               'version', current_setting('server_version'),
               'started_at', pg_postmaster_start_time(),
               'size_bytes', pg_database_size(current_database()),
               'max_connections', current_setting('max_connections')::int,
               'connections', a.total,
               'active', a.active,
               'idle', a.idle,
               'idle_in_transaction', a.idle_in_transaction,
               'waiting', a.waiting,
               'longest_query_seconds', a.longest,
               'commits', d.xact_commit,
               'rollbacks', d.xact_rollback,
               'blocks_read', d.blks_read,
               'blocks_hit', d.blks_hit,
               'rows_returned', d.tup_returned,
               'rows_fetched', d.tup_fetched,
               'rows_inserted', d.tup_inserted,
               'rows_updated', d.tup_updated,
               'rows_deleted', d.tup_deleted,
               'deadlocks', d.deadlocks,
               'temp_bytes', d.temp_bytes,
               'tables', coalesce((select jsonb_agg(jsonb_build_object('name', t.relname, 'bytes', t.bytes, 'rows', t.n_live_tup)
                                                    order by t.bytes desc)
                                   from (select s.relname, s.n_live_tup, pg_total_relation_size(s.relid) as bytes
                                         from pg_stat_user_tables s
                                         where s.schemaname = 'public'
                                         order by pg_total_relation_size(s.relid) desc
                                         limit 8) t), '[]'::jsonb))
from pg_stat_database d
         cross join lateral (select count(*)                                                       as total,
                                    count(*) filter (where p.state = 'active')                     as active,
                                    count(*) filter (where p.state = 'idle')                       as idle,
                                    count(*) filter (where p.state like 'idle in transaction%')    as idle_in_transaction,
                                    count(*) filter (where p.wait_event_type = 'Lock')             as waiting,
                                    coalesce(extract(epoch from max(now() - p.query_start)
                                                                filter (where p.state = 'active'
                                                                    and p.backend_type = 'client backend'
                                                                    and p.pid <> pg_backend_pid())), 0) as longest
                             from pg_stat_activity p
                             where p.datname = current_database()) a
where d.datname = current_database();
$$;

create function system_storage_stats() returns jsonb
    language sql
    stable
    security definer
    set search_path = public
as
$$
select jsonb_build_object(
               'files', count(*) filter (where status = 'uploaded'),
               'bytes', coalesce(sum(size_bytes) filter (where status = 'uploaded'), 0),
               'pending_files', count(*) filter (where status = 'pending'),
               'evidence_files', count(*) filter (where status = 'uploaded' and kind = 'evidence'))
from complaint_files;
$$;
