create function other_active_superusers(p_excluded uuid) returns bigint
    language sql
    stable
    security definer
    set search_path = public
as
$$
select count(distinct ur.user_id)
from user_roles ur
         join roles r on r.id = ur.role_id
         join users u on u.id = ur.user_id
where r.is_superuser
  and u.enabled
  and ur.user_id <> p_excluded;
$$;

create function set_updated_at() returns trigger
    language plpgsql
    set search_path = public
as
$$
begin
    new.updated_at := now();
    return new;
end;
$$;

create trigger trg_roles_updated before update on roles for each row execute function set_updated_at();
create trigger trg_users_updated before update on users for each row execute function set_updated_at();
create trigger trg_departments_updated before update on departments for each row execute function set_updated_at();
create trigger trg_complaint_files_updated before update on complaint_files for each row execute function set_updated_at();

create function role_has_protected_holder(p_role uuid, p_action permission_action) returns boolean
    language sql
    stable
    security definer
    set search_path = public
as
$$
select exists(select 1
              from user_roles ur
              where ur.role_id = p_role
                and ur.user_id <> uid()
                and not can_manage_user(ur.user_id, p_action));
$$;

create function require_role_holders_manageable(p_role uuid, p_action permission_action) returns void
    language plpgsql
    stable
    security definer
    set search_path = public
as
$$
begin
    if not is_superuser(uid()) and role_has_protected_holder(p_role, p_action) then
        raise exception 'INSUFFICIENT_LEVEL: this role is held by users you are not allowed to manage'
            using errcode = '42501';
    end if;
end;
$$;

create function guard_roles() returns trigger
    language plpgsql
    security definer
    set search_path = public
as
$$
declare
    v_super boolean;
begin
    if is_internal() then
        return coalesce(new, old);
    end if;

    v_super := is_superuser(uid());

    if tg_op = 'DELETE' then
        if old.is_system then
            raise exception 'SYSTEM_ROLE_PROTECTED: system roles cannot be deleted' using errcode = '42501';
        end if;
        perform require_manage_role(old.id, old.level, 'delete', 'roles');
        perform require_role_holders_manageable(old.id, 'delete');
        return old;
    end if;

    if tg_op = 'INSERT' then
        new.is_system := false;
        if new.is_superuser and not v_super then
            raise exception 'INSUFFICIENT_LEVEL: only a system admin can create a superuser role' using errcode = '42501';
        end if;
        if new.level < 1 and not v_super then
            raise exception 'INSUFFICIENT_LEVEL: level 0 is reserved for system administrators' using errcode = '42501';
        end if;
        perform require_manage_role(new.id, new.level, 'write', 'roles');
        return new;
    end if;

    if new.is_superuser <> old.is_superuser or new.is_system <> old.is_system then
        raise exception 'SYSTEM_ROLE_PROTECTED: role flags are immutable' using errcode = '42501';
    end if;
    if old.is_system and (new.name <> old.name or new.level <> old.level) then
        raise exception 'SYSTEM_ROLE_PROTECTED: system role name and level are immutable' using errcode = '42501';
    end if;
    if new.level < 1 and not old.is_system and not v_super then
        raise exception 'INSUFFICIENT_LEVEL: level 0 is reserved for system administrators' using errcode = '42501';
    end if;
    perform require_manage_role(old.id, old.level, 'update', 'roles');
    if new.level <> old.level then
        perform require_manage_role(old.id, new.level, 'update', 'roles');
    end if;
    perform require_role_holders_manageable(old.id, 'update');
    return new;
end;
$$;

create trigger trg_guard_roles
    before insert or update or delete
    on roles
    for each row
execute function guard_roles();

create function guard_user_roles() returns trigger
    language plpgsql
    security definer
    set search_path = public
as
$$
declare
    v_role  roles%rowtype;
    v_user  uuid := coalesce(new.user_id, old.user_id);
    v_super boolean;
begin
    if is_internal() then
        return coalesce(new, old);
    end if;

    select * into v_role from roles where id = coalesce(new.role_id, old.role_id);
    v_super := is_superuser(uid());

    if tg_op = 'DELETE' then
        if not exists(select 1 from users where id = old.user_id) or v_role.id is null then
            return old;
        end if;
        if old.user_id = uid() and not v_super then
            raise exception 'SELF_ROLE_CHANGE: you cannot change your own roles' using errcode = '42501';
        end if;
        perform require_manage_user(old.user_id, 'delete');
        perform require_manage_role(v_role.id, v_role.level, 'delete', 'roles');
        if v_role.is_superuser and other_active_superusers(old.user_id) = 0 then
            raise exception 'LAST_SYSTEM_ADMIN: the last system admin cannot be removed' using errcode = '42501';
        end if;
        return old;
    end if;

    if tg_op = 'UPDATE' and (new.user_id <> old.user_id or new.role_id <> old.role_id) then
        raise exception 'user role assignments are immutable, remove and assign instead' using errcode = '42501';
    end if;
    if v_user = uid() and not v_super then
        raise exception 'SELF_ROLE_CHANGE: you cannot change your own roles' using errcode = '42501';
    end if;
    if v_role.is_superuser and not v_super then
        raise exception 'INSUFFICIENT_LEVEL: only a system admin can assign a superuser role' using errcode = '42501';
    end if;
    perform require_manage_user(new.user_id, 'write');
    perform require_manage_role(v_role.id, v_role.level, 'write', 'roles');
    return new;
end;
$$;

create trigger trg_guard_user_roles
    before insert or update or delete
    on user_roles
    for each row
execute function guard_user_roles();

create function guard_role_permission() returns trigger
    language plpgsql
    security definer
    set search_path = public
as
$$
declare
    v_role_id uuid := coalesce(new.role_id, old.role_id);
    v_role    roles%rowtype;
begin
    if is_internal() then
        return coalesce(new, old);
    end if;

    select * into v_role from roles where id = v_role_id;

    if v_role.id is null then
        return coalesce(new, old);
    end if;

    if v_role.is_superuser then
        raise exception 'SYSTEM_ROLE_PROTECTED: superuser role permissions are implicit' using errcode = '42501';
    end if;

    if tg_op = 'DELETE' and not exists(select 1 from permissions where id = old.permission_id) then
        return old;
    end if;

    perform require_manage_role(v_role.id, v_role.level, 'update', 'role permissions');
    perform require_role_holders_manageable(v_role.id, 'update');

    if tg_op <> 'DELETE' and not is_superuser(uid()) and not exists(select 1
                                                                     from user_roles ur
                                                                              join role_permission rp on rp.role_id = ur.role_id
                                                                     where ur.user_id = uid()
                                                                       and rp.permission_id = new.permission_id) then
        raise exception 'PERMISSION_ESCALATION: you cannot grant a permission you do not hold'
            using errcode = '42501';
    end if;

    return coalesce(new, old);
end;
$$;

create trigger trg_guard_role_permission
    before insert or update or delete
    on role_permission
    for each row
execute function guard_role_permission();

create function guard_users() returns trigger
    language plpgsql
    security definer
    set search_path = public
as
$$
begin
    if is_internal() then
        return coalesce(new, old);
    end if;

    if tg_op = 'INSERT' then
        return new;
    end if;

    if tg_op = 'DELETE' then
        perform require_manage_user(old.id, 'delete');
        if is_superuser(old.id) and other_active_superusers(old.id) = 0 then
            raise exception 'LAST_SYSTEM_ADMIN: the last system admin cannot be deleted' using errcode = '42501';
        end if;
        return old;
    end if;

    perform require_manage_user(old.id, 'update');

    if (new.enabled is distinct from old.enabled
        or new.verified is distinct from old.verified
        or new.email is distinct from old.email)
        and not has_scope('users', 'update', array ['department', 'all']::permission_scope[], to_jsonb(old)) then
        raise exception 'INSUFFICIENT_LEVEL: you cannot change email, enabled or verified flags'
            using errcode = '42501';
    end if;

    if new.enabled is distinct from old.enabled and old.id = uid() and not is_superuser(uid()) then
        raise exception 'SELF_PROTECTED: you cannot enable or disable your own account' using errcode = '42501';
    end if;

    if new.enabled = false and old.enabled and is_superuser(old.id) and other_active_superusers(old.id) = 0 then
        raise exception 'LAST_SYSTEM_ADMIN: the last system admin cannot be disabled' using errcode = '42501';
    end if;

    return new;
end;
$$;

create trigger trg_guard_users
    before insert or update or delete
    on users
    for each row
execute function guard_users();

create function guard_department_users() returns trigger
    language plpgsql
    security definer
    set search_path = public
as
$$
begin
    if is_internal() then
        return coalesce(new, old);
    end if;

    if tg_op = 'DELETE' then
        if not exists(select 1 from users where id = old.user_id)
            or not exists(select 1 from departments where id = old.department_id) then
            return old;
        end if;
        perform require_manage_user(old.user_id, 'delete');
        return old;
    end if;

    if tg_op = 'UPDATE' then
        perform require_manage_user(old.user_id, 'update');
    end if;
    perform require_manage_user(new.user_id, 'write');
    return new;
end;
$$;

create trigger trg_guard_department_users
    before insert or update or delete
    on department_users
    for each row
execute function guard_department_users();

create function complaints_before_insert() returns trigger
    language plpgsql
    security definer
    set search_path = public
as
$$
begin
    if is_internal() then
        return new;
    end if;

    new.raised_by := uid();
    if new.student_id is null then
        new.student_id := new.raised_by;
    end if;
    new.reveal_reporter := exists(select 1
                                  from user_roles ur
                                           join roles r on r.id = ur.role_id
                                  where ur.user_id = new.raised_by
                                    and r.reveal_identity);

    if new.department_id is null then
        select department_id
        into new.department_id
        from department_users
        where user_id = new.student_id
        order by department_id
        limit 1;
    end if;

    if not has_scope('complaints', 'write', array ['department', 'all']::permission_scope[], to_jsonb(new)) then
        new.status := 'pending';
        new.assigned_to := null;
        new.resolution := null;
        new.resolved_at := null;
    end if;

    if new.status in ('resolved', 'rejected') then
        new.resolved_at := coalesce(new.resolved_at, now());
    end if;
    return new;
end;
$$;

create trigger trg_complaints_before_insert
    before insert
    on complaints
    for each row
execute function complaints_before_insert();

create function complaints_before_update() returns trigger
    language plpgsql
    security definer
    set search_path = public
as
$$
begin
    if is_internal() then
        return new;
    end if;

    if new.raised_by is distinct from old.raised_by then
        raise exception 'raised_by is immutable' using errcode = '42501';
    end if;

    if new.reveal_reporter is distinct from old.reveal_reporter
        and not has_scope('complaint_reporter', 'update', array ['department', 'all']::permission_scope[], to_jsonb(old)) then
        raise exception 'INSUFFICIENT_LEVEL: you cannot change reporter visibility' using errcode = '42501';
    end if;

    if not has_scope('complaints', 'update', array ['department', 'all']::permission_scope[], to_jsonb(old)) then
        if old.status <> 'pending' then
            raise exception 'COMPLAINT_LOCKED: complaint is already being processed' using errcode = '42501';
        end if;
        if new.status <> old.status
            or new.assigned_to is distinct from old.assigned_to
            or new.resolution is distinct from old.resolution
            or new.student_id <> old.student_id
            or new.department_id is distinct from old.department_id then
            raise exception 'INSUFFICIENT_LEVEL: you cannot change workflow fields' using errcode = '42501';
        end if;
    end if;

    new.updated_at := now();

    if new.status <> old.status then
        if new.status in ('resolved', 'rejected') then
            new.resolved_at := now();
        else
            new.resolved_at := null;
        end if;
    end if;
    return new;
end;
$$;

create trigger trg_complaints_before_update
    before update
    on complaints
    for each row
execute function complaints_before_update();

create function complaints_record_history() returns trigger
    language plpgsql
    security definer
    set search_path = public
as
$$
begin
    if tg_op = 'INSERT' then
        insert into complaint_history(complaint_id, actor_id, from_status, to_status, note)
        values (new.id, uid(), null, new.status, 'Complaint filed');
    elsif new.status <> old.status then
        insert into complaint_history(complaint_id, actor_id, from_status, to_status, note)
        values (new.id, uid(), old.status, new.status, nullif(current_setting('app.status_note', true), ''));
    end if;
    return null;
end;
$$;

create trigger trg_complaints_history
    after insert or update
    on complaints
    for each row
execute function complaints_record_history();

create function complaint_children_before_insert() returns trigger
    language plpgsql
    set search_path = public
as
$$
begin
    if tg_table_name = 'complaint_comments' then
        new.author_id := coalesce(uid(), new.author_id);
    else
        new.uploaded_by := coalesce(uid(), new.uploaded_by);
        if not is_internal() then
            new.status := 'pending';
            new.kind := case when complaint_staff(new.complaint_id) then 'evidence' else 'attachment' end;
        end if;
    end if;
    return new;
end;
$$;

create trigger trg_comments_author
    before insert
    on complaint_comments
    for each row
execute function complaint_children_before_insert();

create trigger trg_files_uploader
    before insert
    on complaint_files
    for each row
execute function complaint_children_before_insert();

create function complaint_children_immutable() returns trigger
    language plpgsql
    set search_path = public
as
$$
begin
    if new.complaint_id <> old.complaint_id then
        raise exception 'complaint_id is immutable' using errcode = '42501';
    end if;
    return new;
end;
$$;

create trigger trg_comments_immutable
    before update
    on complaint_comments
    for each row
execute function complaint_children_immutable();

create trigger trg_files_immutable
    before update
    on complaint_files
    for each row
execute function complaint_children_immutable();

create function guard_files_update() returns trigger
    language plpgsql
    set search_path = public
as
$$
begin
    if new.file_url <> old.file_url or new.kind <> old.kind or new.size_bytes <> old.size_bytes then
        raise exception 'file storage fields are immutable' using errcode = '42501';
    end if;
    if old.status = 'uploaded' and new.status = 'pending' then
        raise exception 'an uploaded file cannot return to pending' using errcode = '42501';
    end if;
    return new;
end;
$$;

create trigger trg_files_guard
    before update
    on complaint_files
    for each row
execute function guard_files_update();

create trigger trg_custom_field_definitions_updated
    before update
    on custom_field_definitions
    for each row
execute function set_updated_at();

create function guard_custom_field_definitions() returns trigger
    language plpgsql
    set search_path = public
as
$$
begin
    if new.entity <> old.entity or new.key <> old.key or new.data_type <> old.data_type then
        raise exception 'entity, key and data type of a custom field cannot be changed' using errcode = '42501';
    end if;
    return new;
end;
$$;

create trigger trg_guard_custom_field_definitions
    before update
    on custom_field_definitions
    for each row
execute function guard_custom_field_definitions();

create function set_custom_value_updated_at() returns trigger
    language plpgsql
    set search_path = public
as
$$
begin
    new.updated_at := now();
    return new;
end;
$$;

create trigger trg_custom_field_values_updated
    before update
    on custom_field_values
    for each row
execute function set_custom_value_updated_at();

create function purge_custom_values() returns trigger
    language plpgsql
    security definer
    set search_path = public
as
$$
begin
    delete from custom_field_values where entity = tg_table_name::text::custom_field_entity and entity_id = old.id;
    return old;
end;
$$;

create trigger trg_users_purge_custom_values after delete on users for each row execute function purge_custom_values();
create trigger trg_departments_purge_custom_values after delete on departments for each row execute function purge_custom_values();
create trigger trg_roles_purge_custom_values after delete on roles for each row execute function purge_custom_values();
create trigger trg_complaints_purge_custom_values after delete on complaints for each row execute function purge_custom_values();

create function revoke_refresh_tokens() returns trigger
    language plpgsql
    security definer
    set search_path = public
as
$$
begin
    if new.password is distinct from old.password or (old.enabled and not new.enabled) then
        update refresh_tokens set revoked_at = now() where user_id = new.id and revoked_at is null;
    end if;
    return null;
end;
$$;

create trigger trg_users_revoke_refresh_tokens
    after update
    on users
    for each row
execute function revoke_refresh_tokens();
