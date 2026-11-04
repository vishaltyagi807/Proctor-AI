create policy users_select_authenticator on users for select to authenticator using (true);

do
$$
    declare
        t record;
    begin
        for t in
            select *
            from (values ('users', 'users', 'can_view_user(id)'),
                         ('user_roles', 'user_roles', 'can_view_user(user_id)'),
                         ('roles', 'roles', 'true'),
                         ('role_permission', 'role_permission', 'true'),
                         ('departments', 'departments', 'true'),
                         ('department_users', 'department_users', 'can_view_user(user_id)'),
                         ('notifications', 'notifications', 'can_view_user(user_id)'),
                         ('notification_devices', 'notification_devices', 'can_view_user(user_id)'),
                         ('integration_settings', 'integrations', 'true')) as v(tbl, ent, vis)
            loop
                execute format(
                        'create policy %1$s_select on %1$I for select to proctor using (can_access(%2$L::entity, ''read'', to_jsonb(%1$I)) and %3$s)',
                        t.tbl, t.ent, t.vis);
                execute format(
                        'create policy %1$s_insert on %1$I for insert to proctor with check (can_access(%2$L::entity, ''write'', to_jsonb(%1$I)))',
                        t.tbl, t.ent);
                execute format(
                        'create policy %1$s_update on %1$I for update to proctor using (can_access(%2$L::entity, ''update'', to_jsonb(%1$I))) with check (can_access(%2$L::entity, ''update'', to_jsonb(%1$I)))',
                        t.tbl, t.ent);
                execute format(
                        'create policy %1$s_delete on %1$I for delete to proctor using (can_access(%2$L::entity, ''delete'', to_jsonb(%1$I)))',
                        t.tbl, t.ent);
            end loop;
    end
$$;

create policy permissions_select on permissions for select to proctor using (uid() is not null);
create policy permissions_insert on permissions for insert to proctor with check (is_superuser(uid()));
create policy permissions_update on permissions for update to proctor using (is_superuser(uid())) with check (is_superuser(uid()));
create policy permissions_delete on permissions for delete to proctor using (is_superuser(uid()));

create policy complaints_select on complaints for select to proctor
    using (can_access('complaints', 'read', to_jsonb(complaints)));
create policy complaints_insert on complaints for insert to proctor
    with check (can_access('complaints', 'write', to_jsonb(complaints)));
create policy complaints_update on complaints for update to proctor
    using (can_access('complaints', 'update', to_jsonb(complaints)))
    with check (can_access('complaints', 'update', to_jsonb(complaints)));
create policy complaints_delete on complaints for delete to proctor
    using (can_access('complaints', 'delete', to_jsonb(complaints)));

create policy complaint_comments_select on complaint_comments for select to proctor
    using (can_access('complaint_comments', 'read', to_jsonb(complaint_comments))
        and (not internal or complaint_staff(complaint_id)));
create policy complaint_comments_insert on complaint_comments for insert to proctor
    with check (can_access('complaint_comments', 'write', to_jsonb(complaint_comments))
        and can_access('complaints', 'read', complaint_row(complaint_id))
        and (not internal or complaint_staff(complaint_id)));
create policy complaint_comments_update on complaint_comments for update to proctor
    using (can_access('complaint_comments', 'update', to_jsonb(complaint_comments)))
    with check (can_access('complaint_comments', 'update', to_jsonb(complaint_comments)));
create policy complaint_comments_delete on complaint_comments for delete to proctor
    using (can_access('complaint_comments', 'delete', to_jsonb(complaint_comments)));

create policy complaint_files_select on complaint_files for select to proctor
    using (can_access('complaint_files', 'read', to_jsonb(complaint_files)));
create policy complaint_files_insert on complaint_files for insert to proctor
    with check (can_access('complaint_files', 'write', to_jsonb(complaint_files))
        and can_access('complaints', 'read', complaint_row(complaint_id)));
create policy complaint_files_update on complaint_files for update to proctor
    using (can_access('complaint_files', 'update', to_jsonb(complaint_files)))
    with check (can_access('complaint_files', 'update', to_jsonb(complaint_files)));
create policy complaint_files_delete on complaint_files for delete to proctor
    using (can_access('complaint_files', 'delete', to_jsonb(complaint_files)));

create policy complaint_history_select on complaint_history for select to proctor
    using (can_access('complaint_history', 'read', to_jsonb(complaint_history)));

create policy complaint_subjects_select on complaint_subjects for select to proctor
    using (can_access('complaints', 'read', complaint_row(complaint_id)));
create policy complaint_subjects_insert on complaint_subjects for insert to proctor
    with check (has_scope('complaints', 'write', array ['department', 'all']::permission_scope[], complaint_row(complaint_id))
        and coalesce((complaint_row(complaint_id) ->> 'raised_by')::uuid = uid(), false)
        and coalesce((complaint_row(complaint_id) ->> 'student_id')::uuid <> user_id, false)
        and can_view_user(user_id));

create policy custom_field_definitions_select on custom_field_definitions for select to proctor
    using (uid() is not null);
create policy custom_field_definitions_insert on custom_field_definitions for insert to proctor
    with check (can_access('custom_fields', 'write', to_jsonb(custom_field_definitions)));
create policy custom_field_definitions_update on custom_field_definitions for update to proctor
    using (can_access('custom_fields', 'update', to_jsonb(custom_field_definitions)))
    with check (can_access('custom_fields', 'update', to_jsonb(custom_field_definitions)));
create policy custom_field_definitions_delete on custom_field_definitions for delete to proctor
    using (can_access('custom_fields', 'delete', to_jsonb(custom_field_definitions)));

create policy custom_field_values_select on custom_field_values for select to proctor
    using (can_access_custom_value(entity, entity_id, false));
create policy custom_field_values_insert on custom_field_values for insert to proctor
    with check (can_access_custom_value(entity, entity_id, true));
create policy custom_field_values_update on custom_field_values for update to proctor
    using (can_access_custom_value(entity, entity_id, true))
    with check (can_access_custom_value(entity, entity_id, true));
create policy custom_field_values_delete on custom_field_values for delete to proctor
    using (can_access_custom_value(entity, entity_id, true));

create policy refresh_tokens_authenticator on refresh_tokens for all to authenticator using (true) with check (true);

create policy notification_preferences_own on notification_preferences for all to proctor
    using (user_id = uid()) with check (user_id = uid());

create policy notification_deliveries_select on notification_deliveries for select to proctor
    using (can_access('integrations', 'read', '{}'::jsonb));

create policy face_audit_log_select on face_audit_log for select to proctor
    using (can_access('face_audit', 'read', to_jsonb(face_audit_log))
        and (subject_id is null or can_view_user(subject_id)));
