create function uuid_or_null(str text) returns uuid
    language plpgsql
    immutable
    set search_path = public
as
$$
begin
    return str::uuid;
exception
    when invalid_text_representation then
        return null;
end;
$$;

create function uid() returns uuid
    language sql
    stable
    set search_path = public
as
$$
select uuid_or_null(current_setting('auth.uid', true));
$$;

create function is_internal() returns boolean
    language sql
    stable
    security definer
    set search_path = public
as
$$
select coalesce((select rolsuper from pg_roles where rolname = session_user), false);
$$;

create function is_active_user(p_user uuid) returns boolean
    language sql
    stable
    security definer
    set search_path = public
as
$$
select exists(select 1 from users where id = p_user and enabled);
$$;

create function is_superuser(p_user uuid) returns boolean
    language sql
    stable
    security definer
    set search_path = public
as
$$
select p_user is not null
    and is_active_user(p_user)
    and exists(select 1
               from user_roles ur
                        join roles r on r.id = ur.role_id
               where ur.user_id = p_user
                 and r.is_superuser);
$$;

create function user_min_level(p_user uuid) returns integer
    language sql
    stable
    security definer
    set search_path = public
as
$$
select coalesce(min(r.level), 2147483647)
from user_roles ur
         join roles r on r.id = ur.role_id
where ur.user_id = p_user;
$$;

create function caller_min_level() returns integer
    language sql
    stable
    security definer
    set search_path = public
as
$$
select user_min_level(uid());
$$;

create function holds_permission(p_entity entity, p_action permission_action) returns boolean
    language sql
    stable
    security definer
    set search_path = public
as
$$
select uid() is not null
    and is_active_user(uid())
    and (is_superuser(uid())
        or exists(select 1
                  from user_roles ur
                           join role_permission rp on rp.role_id = ur.role_id
                           join permissions p on p.id = rp.permission_id
                  where ur.user_id = uid()
                    and p.entity = p_entity
                    and p.action = p_action));
$$;

create function shares_department(p_a uuid, p_b uuid) returns boolean
    language sql
    stable
    security definer
    set search_path = public
as
$$
select exists(select 1
              from department_users a
                       join department_users b on a.department_id = b.department_id
              where a.user_id = p_a
                and b.user_id = p_b);
$$;

create function in_department(p_user uuid, p_department uuid) returns boolean
    language sql
    stable
    security definer
    set search_path = public
as
$$
select p_department is not null
    and exists(select 1
               from department_users
               where user_id = p_user
                 and department_id = p_department);
$$;

create function action_verb(p_action permission_action) returns text
    language sql
    immutable
    set search_path = public
as
$$
select case p_action
           when 'read' then 'view'
           when 'write' then 'assign or create'
           when 'update' then 'modify'
           when 'delete' then 'remove'
           end;
$$;

create function shares_role_at(p_a uuid, p_b uuid, p_level integer) returns boolean
    language sql
    stable
    security definer
    set search_path = public
as
$$
select exists(select 1
              from user_roles a
                       join user_roles b on b.role_id = a.role_id
                       join roles r on r.id = a.role_id
              where a.user_id = p_a
                and b.user_id = p_b
                and r.level = p_level);
$$;

create function user_relation(p_target uuid) returns text
    language plpgsql
    stable
    security definer
    set search_path = public
as
$$
declare
    v        uuid := uid();
    v_caller integer;
    v_target integer;
begin
    if v is null or p_target is null then
        return 'none';
    end if;
    if p_target = v then
        return 'self';
    end if;
    v_caller := caller_min_level();
    v_target := user_min_level(p_target);
    if v_target < v_caller then
        return 'senior';
    end if;
    if v_target > v_caller then
        return 'junior';
    end if;
    if shares_role_at(v, p_target, v_caller) then
        return 'same_role';
    end if;
    return 'parallel_role';
end;
$$;

create function role_relation(p_role uuid, p_level integer) returns text
    language plpgsql
    stable
    security definer
    set search_path = public
as
$$
declare
    v_caller integer;
begin
    if uid() is null or p_level is null then
        return 'none';
    end if;
    v_caller := caller_min_level();
    if p_level < v_caller then
        return 'senior';
    end if;
    if p_level > v_caller then
        return 'junior';
    end if;
    if exists(select 1 from user_roles where user_id = uid() and role_id = p_role) then
        return 'same_role';
    end if;
    return 'parallel_role';
end;
$$;

create function holds_relation_permission(p_entity entity, p_action permission_action, p_target uuid) returns boolean
    language sql
    stable
    security definer
    set search_path = public
as
$$
select uid() is not null
    and is_active_user(uid())
    and (is_superuser(uid())
        or exists(select 1
                  from user_roles ur
                           join role_permission rp on rp.role_id = ur.role_id
                           join permissions p on p.id = rp.permission_id
                  where ur.user_id = uid()
                    and p.entity = p_entity
                    and p.action = p_action
                    and (p.scope = 'all'
                      or (p.scope = 'department' and p_target is not null and shares_department(uid(), p_target)))));
$$;

create function relation_entity(p_relation text) returns entity
    language sql
    immutable
    set search_path = public
as
$$
select case p_relation
           when 'same_role' then 'same_role_peers'::entity
           when 'parallel_role' then 'parallel_role_peers'::entity
           when 'senior' then 'senior_users'::entity
           end;
$$;

create function can_view_user(p_target uuid) returns boolean
    language plpgsql
    stable
    security definer
    set search_path = public
as
$$
declare
    v_relation text;
begin
    if p_target is null or uid() is null or not is_active_user(uid()) then
        return false;
    end if;
    if p_target = uid() or is_superuser(uid()) then
        return true;
    end if;
    v_relation := user_relation(p_target);
    if v_relation = 'junior' then
        return true;
    end if;
    return holds_relation_permission(relation_entity(v_relation), 'read', p_target);
end;
$$;

create function can_manage_user(p_target uuid, p_action permission_action) returns boolean
    language plpgsql
    stable
    security definer
    set search_path = public
as
$$
declare
    v_relation text;
begin
    if p_target is null or uid() is null or not is_active_user(uid()) then
        return false;
    end if;
    if p_target = uid() or is_superuser(uid()) then
        return true;
    end if;
    v_relation := user_relation(p_target);
    if v_relation = 'junior' then
        return true;
    end if;
    if v_relation in ('same_role', 'parallel_role') then
        return can_view_user(p_target)
            and holds_relation_permission(relation_entity(v_relation), p_action, p_target);
    end if;
    return false;
end;
$$;

create function can_manage_role(p_role uuid, p_level integer, p_action permission_action) returns boolean
    language plpgsql
    stable
    security definer
    set search_path = public
as
$$
declare
    v_relation text;
begin
    if uid() is null or not is_active_user(uid()) or p_level is null then
        return false;
    end if;
    if is_superuser(uid()) then
        return true;
    end if;
    v_relation := role_relation(p_role, p_level);
    if v_relation = 'junior' then
        return true;
    end if;
    if v_relation in ('same_role', 'parallel_role') then
        return holds_relation_permission(relation_entity(v_relation), p_action, null);
    end if;
    return false;
end;
$$;

create function require_manage_user(p_target uuid, p_action permission_action) returns void
    language plpgsql
    stable
    security definer
    set search_path = public
as
$$
declare
    v_relation text;
begin
    if can_manage_user(p_target, p_action) then
        return;
    end if;
    v_relation := user_relation(p_target);
    if v_relation = 'same_role' then
        raise exception 'PEER_PROTECTED: you are not allowed to % people who share your role', action_verb(p_action)
            using errcode = '42501';
    end if;
    if v_relation = 'parallel_role' then
        raise exception 'PEER_PROTECTED: you are not allowed to % people in parallel roles at your level', action_verb(p_action)
            using errcode = '42501';
    end if;
    raise exception 'INSUFFICIENT_LEVEL: you cannot % users ranked above you', action_verb(p_action)
        using errcode = '42501';
end;
$$;

create function require_manage_role(p_role uuid, p_level integer, p_action permission_action, p_what text) returns void
    language plpgsql
    stable
    security definer
    set search_path = public
as
$$
declare
    v_relation text;
begin
    if can_manage_role(p_role, p_level, p_action) then
        return;
    end if;
    v_relation := role_relation(p_role, p_level);
    if v_relation = 'same_role' then
        raise exception 'PEER_PROTECTED: you are not allowed to % % you hold yourself', action_verb(p_action), p_what
            using errcode = '42501';
    end if;
    if v_relation = 'parallel_role' then
        raise exception 'PEER_PROTECTED: you are not allowed to % parallel % at your level', action_verb(p_action), p_what
            using errcode = '42501';
    end if;
    raise exception 'INSUFFICIENT_LEVEL: you cannot % % ranked above you (target level %, your level %)',
        action_verb(p_action), p_what, p_level, caller_min_level() using errcode = '42501';
end;
$$;

create function complaint_row(p_id uuid) returns jsonb
    language sql
    stable
    security definer
    set search_path = public
as
$$
select to_jsonb(c)
from complaints c
where c.id = p_id;
$$;

create function is_complaint_subject(p_complaint uuid, p_user uuid) returns boolean
    language sql
    stable
    security definer
    set search_path = public
as
$$
select p_complaint is not null
           and p_user is not null
           and exists(select 1 from complaint_subjects where complaint_id = p_complaint and user_id = p_user);
$$;

create function user_name(p_id uuid) returns text
    language sql
    stable
    security definer
    set search_path = public
as
$$
select name
from users
where id = p_id;
$$;

create function department_name(p_id uuid) returns text
    language sql
    stable
    security definer
    set search_path = public
as
$$
select name
from departments
where id = p_id;
$$;

create function row_scope_match(
    p_entity entity,
    p_scope permission_scope,
    p_action permission_action,
    p_row jsonb
) returns boolean
    language plpgsql
    stable
    security definer
    set search_path = public
as
$$
declare
    v uuid := uid();
begin
    if v is null then
        return false;
    end if;

    if p_scope = 'all' then
        return true;
    end if;

    if p_entity = 'dashboard' then
        return true;
    end if;

    if p_scope = 'own' then
        case p_entity
            when 'users' then return coalesce((p_row ->> 'id')::uuid = v, false);
            when 'user_roles', 'department_users', 'notifications', 'notification_devices' then
                return coalesce((p_row ->> 'user_id')::uuid = v, false);
            when 'roles' then return exists(select 1
                                            from user_roles
                                            where user_id = v
                                              and role_id = (p_row ->> 'id')::uuid);
            when 'role_permission' then return exists(select 1
                                                      from user_roles
                                                      where user_id = v
                                                        and role_id = (p_row ->> 'role_id')::uuid);
            when 'departments' then return in_department(v, (p_row ->> 'id')::uuid);
            when 'face_recognition', 'face_live_recognition', 'face_match_details', 'face_enrollments',
                'face_enroll_lock' then return coalesce((p_row ->> 'id')::uuid = v, false);
            when 'face_audit' then
                return coalesce((p_row ->> 'actor_id')::uuid = v or (p_row ->> 'subject_id')::uuid = v, false);
            when 'complaints' then
                if p_action = 'read' then
                    return coalesce((p_row ->> 'student_id')::uuid = v
                                        or (p_row ->> 'raised_by')::uuid = v
                                        or (p_row ->> 'assigned_to')::uuid = v, false)
                        or is_complaint_subject((p_row ->> 'id')::uuid, v);
                elsif p_action = 'write' then
                    return coalesce((p_row ->> 'raised_by')::uuid = v and (p_row ->> 'student_id')::uuid = v, false);
                end if;
                return coalesce((p_row ->> 'raised_by')::uuid = v, false);
            when 'complaint_reporter' then
                return coalesce((p_row ->> 'student_id')::uuid = v or (p_row ->> 'raised_by')::uuid = v, false)
                    or is_complaint_subject((p_row ->> 'id')::uuid, v);
            when 'complaint_comments' then
                if p_action <> 'read' then
                    return coalesce((p_row ->> 'author_id')::uuid = v, false);
                end if;
                return row_scope_match('complaints', 'own', 'read', complaint_row((p_row ->> 'complaint_id')::uuid));
            when 'complaint_files' then
                if p_action <> 'read' then
                    return coalesce((p_row ->> 'uploaded_by')::uuid = v, false);
                end if;
                return row_scope_match('complaints', 'own', 'read', complaint_row((p_row ->> 'complaint_id')::uuid));
            when 'complaint_history' then
                return row_scope_match('complaints', 'own', 'read', complaint_row((p_row ->> 'complaint_id')::uuid));
            else return false;
            end case;
    end if;

    case p_entity
        when 'users' then
            if p_action = 'write' then
                return true;
            end if;
            return shares_department(v, (p_row ->> 'id')::uuid);
        when 'user_roles', 'department_users' then
            if p_entity = 'department_users' then
                return in_department(v, (p_row ->> 'department_id')::uuid);
            end if;
            return shares_department(v, (p_row ->> 'user_id')::uuid);
        when 'departments' then return in_department(v, (p_row ->> 'id')::uuid);
        when 'face_recognition', 'face_live_recognition', 'face_match_details', 'face_enrollments',
            'face_enroll_lock' then
            return coalesce((p_row ->> 'id')::uuid = v, false) or shares_department(v, (p_row ->> 'id')::uuid);
        when 'face_audit' then
            return coalesce((p_row ->> 'actor_id')::uuid = v or (p_row ->> 'subject_id')::uuid = v, false)
                or shares_department(v, (p_row ->> 'actor_id')::uuid)
                or shares_department(v, (p_row ->> 'subject_id')::uuid);
        when 'notifications', 'notification_devices' then return shares_department(v, (p_row ->> 'user_id')::uuid);
        when 'complaints', 'complaint_reporter' then return in_department(v, (p_row ->> 'department_id')::uuid);
        when 'complaint_comments', 'complaint_files', 'complaint_history' then
            return row_scope_match('complaints', 'department', 'read',
                                   complaint_row((p_row ->> 'complaint_id')::uuid));
        else return false;
        end case;
end;
$$;

create function can_access(
    p_entity entity,
    p_action permission_action,
    p_row jsonb default '{}'::jsonb
) returns boolean
    language plpgsql
    stable
    security definer
    set search_path = public
as
$$
declare
    v uuid := uid();
begin
    if v is null or not is_active_user(v) then
        return false;
    end if;

    if is_superuser(v) then
        return true;
    end if;

    return exists(select 1
                  from user_roles ur
                           join role_permission rp on rp.role_id = ur.role_id
                           join permissions p on p.id = rp.permission_id
                  where ur.user_id = v
                    and p.entity = p_entity
                    and p.action = p_action
                    and (p.scope = 'all' or row_scope_match(p_entity, p.scope, p_action, p_row)));
end;
$$;

create function has_scope(
    p_entity entity,
    p_action permission_action,
    p_scopes permission_scope[],
    p_row jsonb default '{}'::jsonb
) returns boolean
    language plpgsql
    stable
    security definer
    set search_path = public
as
$$
declare
    v uuid := uid();
begin
    if v is null or not is_active_user(v) then
        return false;
    end if;

    if is_superuser(v) then
        return true;
    end if;

    return exists(select 1
                  from user_roles ur
                           join role_permission rp on rp.role_id = ur.role_id
                           join permissions p on p.id = rp.permission_id
                  where ur.user_id = v
                    and p.entity = p_entity
                    and p.action = p_action
                    and p.scope = any (p_scopes)
                    and (p.scope = 'all' or row_scope_match(p_entity, p.scope, p_action, p_row)));
end;
$$;

create function complaint_staff(p_complaint uuid) returns boolean
    language sql
    stable
    security definer
    set search_path = public
as
$$
select has_scope('complaints', 'update', array ['department', 'all']::permission_scope[], complaint_row(p_complaint));
$$;

create function can_see_reporter(p_complaint uuid) returns boolean
    language plpgsql
    stable
    security definer
    set search_path = public
as
$$
declare
    v uuid := uid();
    c jsonb := complaint_row(p_complaint);
begin
    if v is null or c is null then
        return false;
    end if;
    if is_superuser(v) then
        return true;
    end if;
    if coalesce((c ->> 'raised_by')::uuid = v, false) then
        return true;
    end if;
    if coalesce((c ->> 'reveal_reporter')::boolean, false)
        and (coalesce((c ->> 'student_id')::uuid = v, false) or is_complaint_subject(p_complaint, v)) then
        return true;
    end if;
    return can_access('complaint_reporter', 'read', c);
end;
$$;

create function visible_actor(p_complaint uuid, p_user uuid) returns uuid
    language sql
    stable
    security definer
    set search_path = public
as
$$
select case
           when p_user is null then null
           when p_user = (select raised_by from complaints where id = p_complaint)
               and not can_see_reporter(p_complaint) then null
           else p_user
           end;
$$;

create function user_department_names(p_user uuid) returns text[]
    language sql
    stable
    security definer
    set search_path = public
as
$$
select coalesce(array_agg(d.name order by d.name), '{}'::text[])
from department_users du
         join departments d on d.id = du.department_id
where du.user_id = p_user;
$$;

create function entity_row(p_entity custom_field_entity, p_id uuid) returns jsonb
    language sql
    stable
    security definer
    set search_path = public
as
$$
select case p_entity
           when 'users' then (select to_jsonb(u) - 'password' from users u where u.id = p_id)
           when 'departments' then (select to_jsonb(d) from departments d where d.id = p_id)
           when 'roles' then (select to_jsonb(r) from roles r where r.id = p_id)
           when 'complaints' then complaint_row(p_id)
           end;
$$;

create function user_role_ids(p_user uuid) returns uuid[]
    language sql
    stable
    security definer
    set search_path = public
as
$$
select coalesce(array_agg(role_id), '{}'::uuid[])
from user_roles
where user_id = p_user;
$$;

create function user_department_ids(p_user uuid) returns uuid[]
    language sql
    stable
    security definer
    set search_path = public
as
$$
select coalesce(array_agg(department_id), '{}'::uuid[])
from department_users
where user_id = p_user;
$$;

create function can_access_custom_value(p_entity custom_field_entity, p_id uuid, p_write boolean) returns boolean
    language plpgsql
    stable
    security definer
    set search_path = public
as
$$
declare
    v_row    jsonb := entity_row(p_entity, p_id);
    v_entity entity := p_entity::text::entity;
begin
    if v_row is null then
        return false;
    end if;
    if not can_access(v_entity, 'read', v_row) then
        return false;
    end if;
    if p_entity = 'users' and not can_view_user(p_id) then
        return false;
    end if;
    if not p_write then
        return true;
    end if;
    if p_entity = 'users' and not can_manage_user(p_id, 'update') then
        return false;
    end if;
    if p_entity = 'roles' and not can_manage_role(p_id, (v_row ->> 'level')::integer, 'update') then
        return false;
    end if;
    return can_access(v_entity, 'update', v_row) or can_access(v_entity, 'write', v_row);
end;
$$;

create function can_manage_face(p_action permission_action, p_target uuid) returns boolean
    language sql
    stable
    security definer
    set search_path = public
as
$$
select can_access('face_enrollments', p_action, jsonb_build_object('id', p_target))
    and can_manage_user(p_target, p_action);
$$;

create function face_privileged(p_action permission_action, p_target uuid) returns boolean
    language sql
    stable
    security definer
    set search_path = public
as
$$
select has_scope('face_enrollments', p_action, array ['department', 'all']::permission_scope[],
                 jsonb_build_object('id', p_target))
    and can_manage_user(p_target, p_action);
$$;

create function can_view_face_enrollment(p_target uuid) returns boolean
    language sql
    stable
    security definer
    set search_path = public
as
$$
select can_access('face_enrollments', 'read', jsonb_build_object('id', p_target))
    and can_view_user(p_target);
$$;

create function can_see_face(p_target uuid, p_live boolean) returns boolean
    language sql
    stable
    security definer
    set search_path = public
as
$$
select is_active_user(p_target)
    and can_access(case when p_live then 'face_live_recognition'::entity else 'face_recognition'::entity end,
                   'read', jsonb_build_object('id', p_target))
    and can_view_user(p_target);
$$;

create function can_see_face_details(p_target uuid) returns boolean
    language sql
    stable
    security definer
    set search_path = public
as
$$
select can_access('face_match_details', 'read', jsonb_build_object('id', p_target));
$$;

create function can_unlock_face(p_target uuid) returns boolean
    language sql
    stable
    security definer
    set search_path = public
as
$$
select can_access('face_enroll_lock', 'update', jsonb_build_object('id', p_target))
    and can_manage_user(p_target, 'update');
$$;

create function log_face_event(
    p_event face_event,
    p_subject uuid,
    p_matched boolean,
    p_confidence real,
    p_faces integer,
    p_detail text
) returns void
    language plpgsql
    security definer
    set search_path = public
as
$$
begin
    if uid() is null then
        raise exception 'Authentication required' using errcode = '42501';
    end if;
    insert into face_audit_log (event, actor_id, subject_id, matched, confidence, faces_detected, detail)
    values (p_event, uid(), p_subject, p_matched, p_confidence, p_faces, left(p_detail, 500));
end;
$$;
