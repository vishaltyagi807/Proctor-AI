do
$$
    declare
        f record;
    begin
        for f in
            select p.oid::regprocedure as sig
            from pg_proc p
                     join pg_namespace n on n.oid = p.pronamespace
            where n.nspname = 'public'
              and p.prokind = 'f'
              and not exists(select 1 from pg_depend d where d.objid = p.oid and d.deptype = 'e')
            loop
                execute format('alter function %s owner to auth_owner', f.sig);
            end loop;
    end
$$;

revoke all on all tables in schema public from public;
revoke all on all functions in schema public from public;

grant usage on schema public to authenticator, proctor, auth_owner;
grant create on schema public to proctor;

do
$$
    begin
        execute format('grant create on database %I to proctor', current_database());
    end
$$;

grant execute on all functions in schema public to proctor, auth_owner;
revoke execute on function crypt(text, text) from proctor;
revoke execute on function purge_stale_uploads(interval) from proctor;
grant execute on function purge_stale_uploads(interval) to proctor;

grant select on users to authenticator;
grant select, insert, update, delete on refresh_tokens to authenticator;
grant update on refresh_tokens to auth_owner;

grant select, insert, update, delete on
    users, roles, role_permission, user_roles, departments, department_users,
    complaints, complaint_comments, complaint_files, permissions,
    custom_field_definitions, custom_field_values, notifications, notification_devices,
    notification_preferences, integration_settings to proctor;
grant select on notification_deliveries to proctor;
grant select on complaint_history to proctor;
grant select, insert on complaint_subjects to proctor;
grant select on face_audit_log to proctor;

grant select on all tables in schema public to auth_owner;
grant insert on complaint_history to auth_owner;
grant insert on face_audit_log to auth_owner;
grant delete on complaint_files to auth_owner;
grant delete on custom_field_values to auth_owner;
grant insert, delete on notifications to auth_owner;
grant insert on notification_deliveries to auth_owner;
grant update on notification_devices to auth_owner;
grant insert, update on notification_devices to auth_owner;

alter default privileges in schema public revoke all on tables from public;
