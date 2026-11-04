create function system_create_notification(
    p_user uuid,
    p_type text,
    p_title text,
    p_body text,
    p_data jsonb,
    p_priority text,
    p_source text,
    p_expires timestamptz
) returns notifications
    language plpgsql
    security definer
    set search_path = public
as
$$
declare
    v_row notifications;
begin
    if not exists(select 1 from users where id = p_user and enabled) then
        return null;
    end if;
    insert into notifications (user_id, type, title, body, data, priority, source, expires_at)
    values (p_user, p_type, p_title, p_body, coalesce(p_data, '{}'::jsonb), coalesce(p_priority, 'normal'), p_source,
            p_expires)
    returning * into v_row;
    return v_row;
end;
$$;

create function system_delivery_profile(p_user uuid)
    returns table
            (
                push_enabled     boolean,
                realtime_enabled boolean,
                muted_types      text[]
            )
    language sql
    stable
    security definer
    set search_path = public
as
$$
select coalesce(p.push_enabled, true), coalesce(p.realtime_enabled, true), coalesce(p.muted_types, '{}'::text[])
from (select 1) x
         left join notification_preferences p on p.user_id = p_user;
$$;

create function system_active_devices(p_user uuid)
    returns table
            (
                id       uuid,
                platform text,
                token    text
            )
    language sql
    stable
    security definer
    set search_path = public
as
$$
select d.id, d.platform, d.token
from notification_devices d
where d.user_id = p_user
  and d.active;
$$;

create function system_deactivate_device(p_token text) returns void
    language sql
    security definer
    set search_path = public
as
$$
update notification_devices
set active = false
where token = p_token;
$$;

create function system_log_delivery(p_notification uuid, p_channel text, p_status text, p_detail text, p_device uuid)
    returns void
    language sql
    security definer
    set search_path = public
as
$$
insert into notification_deliveries (notification_id, channel, status, detail, device_id)
values (p_notification, p_channel, p_status, left(p_detail, 500), p_device);
$$;

create function system_integration(p_provider text)
    returns table
            (
                enabled           boolean,
                config            jsonb,
                secret_ciphertext text
            )
    language sql
    stable
    security definer
    set search_path = public
as
$$
select i.enabled, i.config, i.secret_ciphertext
from integration_settings i
where i.provider = p_provider;
$$;

create function register_device(p_platform text, p_token text, p_name text) returns uuid
    language plpgsql
    security definer
    set search_path = public
as
$$
declare
    v_user uuid := uid();
    v_id   uuid;
begin
    if v_user is null or not is_active_user(v_user) then
        raise exception 'Authentication required' using errcode = '42501';
    end if;
    insert into notification_devices (user_id, platform, token, device_name)
    values (v_user, p_platform, p_token, p_name)
    on conflict (token) do update set user_id      = excluded.user_id,
                                      platform     = excluded.platform,
                                      device_name  = coalesce(excluded.device_name, notification_devices.device_name),
                                      active       = true,
                                      last_seen_at = now()
    returning id into v_id;
    return v_id;
end;
$$;

create function purge_old_notifications(p_days integer) returns bigint
    language sql
    security definer
    set search_path = public
as
$$
with removed as (
    delete from notifications
        where created_at < now() - make_interval(days => p_days)
            or (expires_at is not null and expires_at < now())
        returning 1)
select count(*)
from removed;
$$;

create function complaint_parties(p_complaint uuid)
    returns table
            (
                student_id    uuid,
                raised_by     uuid,
                assigned_to   uuid,
                department_id uuid,
                title         text,
                subject_ids   uuid[]
            )
    language sql
    stable
    security definer
    set search_path = public
as
$$
select c.student_id,
       c.raised_by,
       c.assigned_to,
       c.department_id,
       c.title,
       array(select s.user_id from complaint_subjects s where s.complaint_id = c.id)
from complaints c
where c.id = p_complaint
  and can_access('complaints', 'read', to_jsonb(c));
$$;

create function resolve_notification_recipients(p_users uuid[], p_roles uuid[], p_departments uuid[]) returns uuid[]
    language plpgsql
    stable
    security definer
    set search_path = public
as
$$
declare
    v_caller uuid := uid();
    v_all    boolean;
    v_dept   boolean;
    v_result uuid[];
begin
    if v_caller is null or not is_active_user(v_caller) then
        raise exception 'Authentication required' using errcode = '42501';
    end if;

    v_all := has_scope('notifications', 'write', array ['all']::permission_scope[], '{}'::jsonb);
    v_dept := not v_all and has_scope('notifications', 'write', array ['department']::permission_scope[],
                                      jsonb_build_object('user_id', v_caller));

    if not v_all and not v_dept then
        raise exception 'You are not allowed to send notifications' using errcode = '42501';
    end if;

    select coalesce(array_agg(distinct u.id), '{}'::uuid[])
    into v_result
    from users u
    where u.enabled
      and (u.id = any (coalesce(p_users, '{}'::uuid[]))
        or exists(select 1
                  from user_roles ur
                  where ur.user_id = u.id
                    and ur.role_id = any (coalesce(p_roles, '{}'::uuid[])))
        or exists(select 1
                  from department_users du
                  where du.user_id = u.id
                    and du.department_id = any (coalesce(p_departments, '{}'::uuid[]))))
      and (v_all or shares_department(v_caller, u.id));

    return v_result;
end;
$$;
