create function purge_stale_uploads(p_older_than interval) returns setof text
    language sql
    security definer
    set search_path = public
as
$$
delete
from complaint_files
where status = 'pending'
  and created_at < now() - p_older_than
returning file_url;
$$;

create function complaint_file_usage(p_complaint uuid)
    returns table
            (
                file_count  bigint,
                total_bytes bigint
            )
    language sql
    stable
    security definer
    set search_path = public
as
$$
select count(*), coalesce(sum(size_bytes), 0)::bigint
from complaint_files
where complaint_id = p_complaint;
$$;
