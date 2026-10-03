insert into permissions (entity, action, scope, description)
select e.entity,
       a.action,
       s.scope,
       initcap(a.action::text) || ' ' || replace(e.entity::text, '_', ' ') || ' (' || s.scope::text || ')'
from unnest(enum_range(null::entity)) as e(entity)
         cross join unnest(enum_range(null::permission_action)) as a(action)
         cross join unnest(enum_range(null::permission_scope)) as s(scope)
where case e.entity
          when 'dashboard' then a.action = 'read'
          when 'permissions' then a.action = 'read' and s.scope = 'all'
          when 'complaint_history' then a.action = 'read'
          when 'complaint_reporter' then a.action in ('read', 'update')
          when 'custom_fields' then s.scope = 'all'
          when 'integrations' then s.scope = 'all'
          when 'face_recognition' then a.action = 'read' and s.scope in ('department', 'all')
          when 'face_live_recognition' then a.action = 'read' and s.scope in ('department', 'all')
          when 'face_match_details' then a.action = 'read' and s.scope in ('department', 'all')
          when 'face_enroll_lock' then a.action = 'update' and s.scope in ('department', 'all')
          when 'face_import' then a.action in ('read', 'write') and s.scope = 'all'
          when 'face_audit' then a.action = 'read'
          when 'same_role_peers' then s.scope in ('department', 'all')
          when 'parallel_role_peers' then s.scope in ('department', 'all')
          when 'senior_users' then a.action = 'read' and s.scope in ('department', 'all')
          when 'system_monitor' then a.action = 'read' and s.scope = 'all'
          else true
          end
on conflict (entity, action, scope) do nothing;

update permissions p
set description = d.description
from (values ('same_role_peers', 'read', 'department', 'See people who share your role, in your departments'),
             ('same_role_peers', 'read', 'all', 'See everyone who shares your role'),
             ('same_role_peers', 'write', 'department', 'Assign roles to people who share your role, in your departments'),
             ('same_role_peers', 'write', 'all', 'Assign roles to anyone who shares your role'),
             ('same_role_peers', 'update', 'department', 'Edit people who share your role, in your departments'),
             ('same_role_peers', 'update', 'all', 'Edit anyone who shares your role, and the role itself'),
             ('same_role_peers', 'delete', 'department', 'Remove people who share your role, in your departments'),
             ('same_role_peers', 'delete', 'all', 'Remove anyone who shares your role, and unassign the role'),
             ('parallel_role_peers', 'read', 'department', 'See people in parallel roles at your level, in your departments'),
             ('parallel_role_peers', 'read', 'all', 'See everyone in parallel roles at your level'),
             ('parallel_role_peers', 'write', 'department', 'Assign roles to people in parallel roles, in your departments'),
             ('parallel_role_peers', 'write', 'all', 'Create parallel roles at your level and assign them'),
             ('parallel_role_peers', 'update', 'department', 'Edit people in parallel roles, in your departments'),
             ('parallel_role_peers', 'update', 'all', 'Edit people in parallel roles and those roles themselves'),
             ('parallel_role_peers', 'delete', 'department', 'Remove people in parallel roles, in your departments'),
             ('parallel_role_peers', 'delete', 'all', 'Remove people in parallel roles and delete those roles'),
             ('senior_users', 'read', 'department', 'See people ranked above you in your departments'),
             ('senior_users', 'read', 'all', 'See everyone ranked above you'),
             ('face_recognition', 'read', 'department', 'Identify people in your departments from uploaded photos'),
             ('face_recognition', 'read', 'all', 'Identify anyone from uploaded photos'),
             ('face_live_recognition', 'read', 'department', 'Identify people in your departments with the live camera'),
             ('face_live_recognition', 'read', 'all', 'Identify anyone with the live camera'),
             ('face_match_details', 'read', 'department', 'See match confidence and email for people in your departments'),
             ('face_match_details', 'read', 'all', 'See match confidence and email for anyone'),
             ('face_enroll_lock', 'update', 'department', 'Reset self-enrollment locks in your departments'),
             ('face_enroll_lock', 'update', 'all', 'Reset anyone''s self-enrollment lock'),
             ('face_audit', 'read', 'own', 'See face activity you performed or that involved you'),
             ('face_audit', 'read', 'department', 'See face activity in your departments'),
             ('face_audit', 'read', 'all', 'See all face recognition activity'),
             ('face_import', 'read', 'all', 'Download the bulk face import template'),
             ('face_import', 'write', 'all', 'Bulk import face photos from a ZIP'),
             ('face_enrollments', 'read', 'own', 'View your own face enrollment'),
             ('face_enrollments', 'read', 'department', 'View face enrollments in your departments'),
             ('face_enrollments', 'read', 'all', 'View all face enrollments'),
             ('face_enrollments', 'write', 'own', 'Enroll your own face (limited attempts)'),
             ('face_enrollments', 'write', 'department', 'Enroll faces of people in your departments'),
             ('face_enrollments', 'write', 'all', 'Enroll faces of anyone'),
             ('face_enrollments', 'update', 'own', 'Replace your own face enrollment (limited attempts)'),
             ('face_enrollments', 'update', 'department', 'Replace face enrollments in your departments'),
             ('face_enrollments', 'update', 'all', 'Replace any face enrollment'),
             ('face_enrollments', 'delete', 'own', 'Remove your own face enrollment'),
             ('face_enrollments', 'delete', 'department', 'Remove face enrollments in your departments'),
             ('face_enrollments', 'delete', 'all', 'Remove any face enrollment'),
             ('system_monitor', 'read', 'all', 'View live CPU, memory, service, database and cache usage')) as d(entity, action, scope, description)
where p.entity = d.entity::entity
  and p.action = d.action::permission_action
  and p.scope = d.scope::permission_scope;

insert into roles (name, description, level, is_system, is_superuser)
values ('system_admin', 'Unrestricted system administrator', 0, true, true),
       ('admin', 'Administrator with the same unrestricted access as the system admin', 0, true, true),
       ('teacher', 'Staff who handle complaints of their departments', 20, false, false),
       ('student', 'Student who files and follows own complaints', 50, false, false)
on conflict (name) do nothing;

insert into role_permission (role_id, permission_id)
select r.id, p.id
from roles r
         join permissions p on p.scope = 'all'
where r.is_superuser
on conflict do nothing;

insert into role_permission (role_id, permission_id)
select r.id, p.id
from roles r
         join permissions p on p.scope = 'department'
where r.name = 'teacher'
  and ((p.entity in ('users', 'departments', 'department_users', 'complaint_history', 'dashboard') and
        p.action = 'read')
    or (p.entity = 'complaint_reporter' and p.action = 'read')
    or (p.entity = 'complaints' and p.action in ('read', 'write', 'update'))
    or (p.entity in ('complaint_comments', 'complaint_files') and p.action in ('read', 'write'))
    or (p.entity in ('face_recognition', 'face_live_recognition', 'face_enrollments', 'face_enroll_lock'))
    or (p.entity = 'face_match_details')
    or (p.entity in ('same_role_peers', 'senior_users') and p.action = 'read'))
on conflict do nothing;

insert into role_permission (role_id, permission_id)
select r.id, p.id
from roles r
         join permissions p on p.scope = 'own'
where r.name = 'teacher'
  and ((p.entity in ('users', 'roles', 'role_permission', 'user_roles') and p.action = 'read')
    or (p.entity = 'users' and p.action = 'update')
    or (p.entity in ('notifications', 'notification_devices'))
    or (p.entity in ('complaint_comments', 'complaint_files') and p.action in ('update', 'delete')))
on conflict do nothing;

insert into role_permission (role_id, permission_id)
select r.id, p.id
from roles r
         join permissions p on p.scope = 'own'
where r.name = 'student'
  and ((p.entity in ('users', 'roles', 'role_permission', 'user_roles', 'departments', 'department_users',
                     'complaint_history', 'dashboard') and p.action = 'read')
    or (p.entity = 'users' and p.action = 'update')
    or (p.entity in ('notifications', 'notification_devices'))
    or (p.entity = 'complaints' and p.action in ('read', 'write', 'update', 'delete'))
    or (p.entity = 'complaint_comments' and p.action in ('read', 'write', 'delete'))
    or (p.entity = 'complaint_files' and p.action in ('read', 'write', 'update', 'delete'))
    or (p.entity = 'face_enrollments' and p.action in ('read', 'write'))
    or (p.entity = 'face_audit' and p.action = 'read'))
on conflict do nothing;

insert into users (email, name, password, enabled, verified)
values ('admin@college.com', 'System Admin', crypt('admin123', gen_salt('bf', 12)), true, true)
on conflict (email) do nothing;

insert into user_roles (user_id, role_id)
select u.id, r.id
from users u
         join roles r on r.name = 'system_admin'
where u.email = 'admin@college.com'
on conflict do nothing;
