create type permission_action as enum ('read', 'write', 'update', 'delete');

create type permission_scope as enum ('own', 'department', 'all');

create type entity as enum (
    'dashboard',
    'users', 'user_roles', 'roles', 'role_permission', 'permissions',
    'departments', 'department_users',
    'complaints', 'complaint_comments', 'complaint_files', 'complaint_history', 'complaint_reporter',
    'custom_fields', 'notifications', 'notification_devices', 'integrations',
    'face_recognition', 'face_live_recognition', 'face_match_details', 'face_enrollments',
    'face_enroll_lock', 'face_import', 'face_audit',
    'same_role_peers', 'parallel_role_peers', 'senior_users',
    'system_monitor'
    );

create type face_event as enum ('recognize', 'live_recognize', 'enroll', 'replace', 'remove', 'unlock', 'bulk_import');

create type custom_field_entity as enum ('users', 'departments', 'roles', 'complaints');

create type custom_field_type as enum ('text', 'number', 'bool', 'date', 'select', 'multi_select');

create type file_status as enum ('pending', 'uploaded');

create type complaint_status as enum ('pending', 'reviewed', 'resolved', 'rejected');

create type complaint_priority as enum ('low', 'medium', 'high', 'urgent');

create table roles
(
    id           uuid primary key     default gen_random_uuid(),
    name         text        not null unique,
    description  text,
    level        integer     not null default 100 check (level >= 0),
    is_system    boolean     not null default false,
    is_superuser boolean     not null default false,
    reveal_identity boolean  not null default false,
    created_at   timestamptz not null default now(),
    updated_at   timestamptz
);

create table permissions
(
    id          uuid primary key          default gen_random_uuid(),
    entity      entity           not null,
    action      permission_action not null,
    scope       permission_scope not null default 'own',
    description text,
    created_at  timestamptz      not null default now(),
    unique (entity, action, scope)
);

create table role_permission
(
    role_id       uuid        not null references roles (id) on delete cascade,
    permission_id uuid        not null references permissions (id) on delete cascade,
    created_at    timestamptz not null default now(),
    primary key (role_id, permission_id)
);

create index idx_role_permission_permission on role_permission (permission_id);

create table users
(
    id         uuid primary key     default gen_random_uuid(),
    email      text        not null unique,
    name       text        not null,
    password   text        not null,
    enabled    boolean     not null default true,
    verified   boolean     not null default false,
    created_at timestamptz not null default now(),
    updated_at timestamptz
);

create index idx_users_name on users (name);
create unique index uq_users_email_lower on users (lower(email));

create table user_roles
(
    user_id    uuid        not null references users (id) on delete cascade,
    role_id    uuid        not null references roles (id) on delete cascade,
    created_at timestamptz not null default now(),
    primary key (user_id, role_id)
);

create index idx_user_roles_role on user_roles (role_id);

create table departments
(
    id          uuid primary key     default gen_random_uuid(),
    name        text        not null,
    code        text        not null unique,
    description text,
    active      boolean     not null default true,
    created_at  timestamptz not null default now(),
    updated_at  timestamptz
);

create table department_users
(
    user_id       uuid not null references users (id) on delete cascade,
    department_id uuid not null references departments (id) on delete cascade,
    primary key (user_id, department_id)
);

create index idx_department_users_department on department_users (department_id);

create table complaints
(
    id            uuid primary key            default gen_random_uuid(),
    title         text               not null,
    description   text               not null,
    category      text               not null default 'general',
    priority      complaint_priority not null default 'medium',
    status        complaint_status   not null default 'pending',
    student_id    uuid               not null references users (id) on delete cascade,
    raised_by     uuid                        default null references users (id) on delete set null,
    reveal_reporter boolean          not null default false,
    assigned_to   uuid                        default null references users (id) on delete set null,
    department_id uuid                        default null references departments (id) on delete set null,
    resolution    text,
    active        boolean            not null default true,
    resolved_at   timestamptz,
    created_at    timestamptz        not null default now(),
    updated_at    timestamptz
);

create index idx_complaints_student on complaints (student_id);
create index idx_complaints_raised_by on complaints (raised_by);
create index idx_complaints_assigned on complaints (assigned_to);
create index idx_complaints_department on complaints (department_id);
create index idx_complaints_status on complaints (status);

create table complaint_comments
(
    id           uuid primary key     default gen_random_uuid(),
    complaint_id uuid        not null references complaints (id) on delete cascade,
    author_id    uuid        references users (id) on delete set null,
    body         text        not null,
    internal     boolean     not null default false,
    created_at   timestamptz not null default now()
);

create index idx_complaint_comments_complaint on complaint_comments (complaint_id);

create table complaint_files
(
    id           uuid primary key     default gen_random_uuid(),
    complaint_id uuid        not null references complaints (id) on delete cascade,
    uploaded_by  uuid        references users (id) on delete set null,
    file_name    text        not null,
    content_type text        not null default 'application/octet-stream',
    size_bytes   bigint      not null default 0,
    status       file_status not null default 'pending',
    kind         text        not null default 'attachment' check (kind in ('attachment', 'evidence')),
    file_url     text        not null,
    created_at   timestamptz not null default now(),
    updated_at   timestamptz
);

create index idx_complaint_files_complaint on complaint_files (complaint_id);

create table complaint_history
(
    id           uuid primary key     default gen_random_uuid(),
    complaint_id uuid        not null references complaints (id) on delete cascade,
    actor_id     uuid        references users (id) on delete set null,
    from_status  complaint_status,
    to_status    complaint_status not null,
    note         text,
    created_at   timestamptz not null default now()
);

create index idx_complaint_history_complaint on complaint_history (complaint_id);

create table complaint_subjects
(
    complaint_id uuid not null references complaints (id) on delete cascade,
    user_id      uuid not null references users (id) on delete cascade,
    primary key (complaint_id, user_id)
);

create index idx_complaint_subjects_user on complaint_subjects (user_id);

alter table roles enable row level security;
alter table permissions enable row level security;
alter table role_permission enable row level security;
alter table users enable row level security;
alter table user_roles enable row level security;
alter table departments enable row level security;
alter table department_users enable row level security;
alter table complaints enable row level security;
alter table complaint_comments enable row level security;
alter table complaint_files enable row level security;
alter table complaint_history enable row level security;
alter table complaint_subjects enable row level security;

create table custom_field_definitions
(
    id                       uuid primary key             default gen_random_uuid(),
    entity                   custom_field_entity not null,
    key                      text                not null check (key ~ '^[a-z][a-z0-9_]{0,49}$'),
    label                    text                not null,
    help_text                text,
    data_type                custom_field_type   not null,
    required                 boolean             not null default false,
    options                  jsonb,
    default_value            jsonb,
    min_value                numeric,
    max_value                numeric,
    max_length               integer check (max_length is null or max_length > 0),
    pattern                  text,
    applies_to_role_id       uuid                         default null references roles (id) on delete cascade,
    applies_to_department_id uuid                         default null references departments (id) on delete cascade,
    sort_order               integer             not null default 0,
    active                   boolean             not null default true,
    created_at               timestamptz         not null default now(),
    updated_at               timestamptz,
    unique (entity, key),
    check (entity <> 'departments' or applies_to_role_id is null),
    check (entity <> 'roles' or applies_to_department_id is null),
    check (data_type not in ('select', 'multi_select') or jsonb_typeof(options) = 'array')
);

create index idx_custom_field_definitions_entity on custom_field_definitions (entity, active);

create table custom_field_values
(
    definition_id uuid                not null references custom_field_definitions (id) on delete cascade,
    entity        custom_field_entity not null,
    entity_id     uuid                not null,
    value         jsonb               not null,
    updated_at    timestamptz         not null default now(),
    primary key (definition_id, entity_id)
);

create index idx_custom_field_values_entity on custom_field_values (entity, entity_id);

alter table custom_field_definitions enable row level security;
alter table custom_field_values enable row level security;

create table refresh_tokens
(
    id          uuid primary key     default gen_random_uuid(),
    user_id     uuid        not null references users (id) on delete cascade,
    family_id   uuid        not null,
    token_hash  text        not null unique,
    issued_at   timestamptz not null default now(),
    expires_at  timestamptz not null,
    revoked_at  timestamptz,
    user_agent  text,
    ip_address  text
);

create index idx_refresh_tokens_user on refresh_tokens (user_id);
create index idx_refresh_tokens_family on refresh_tokens (family_id);
create index idx_refresh_tokens_expires on refresh_tokens (expires_at);

alter table refresh_tokens enable row level security;

create table notifications
(
    id         uuid primary key     default gen_random_uuid(),
    user_id    uuid        not null references users (id) on delete cascade,
    type       text        not null check (length(type) between 1 and 100),
    title      text        not null,
    body       text,
    data       jsonb       not null default '{}'::jsonb,
    priority   text        not null default 'normal' check (priority in ('low', 'normal', 'high')),
    source     text,
    read_at    timestamptz,
    expires_at timestamptz,
    created_at timestamptz not null default now()
);

create index idx_notifications_user on notifications (user_id, created_at desc);
create index idx_notifications_unread on notifications (user_id) where read_at is null;

create table notification_devices
(
    id           uuid primary key     default gen_random_uuid(),
    user_id      uuid        not null references users (id) on delete cascade,
    platform     text        not null check (platform in ('android', 'ios', 'web')),
    token        text        not null unique,
    device_name  text,
    active       boolean     not null default true,
    last_seen_at timestamptz not null default now(),
    created_at   timestamptz not null default now()
);

create index idx_notification_devices_user on notification_devices (user_id) where active;

create table notification_preferences
(
    user_id          uuid primary key references users (id) on delete cascade,
    push_enabled     boolean     not null default true,
    realtime_enabled boolean     not null default true,
    muted_types      text[]      not null default '{}',
    updated_at       timestamptz not null default now()
);

create table integration_settings
(
    provider          text primary key check (provider in ('fcm')),
    enabled           boolean     not null default false,
    config            jsonb       not null default '{}'::jsonb,
    secret_ciphertext text,
    updated_by        uuid references users (id) on delete set null,
    updated_at        timestamptz not null default now()
);

create table notification_deliveries
(
    id              uuid primary key     default gen_random_uuid(),
    notification_id uuid references notifications (id) on delete cascade,
    channel         text        not null check (channel in ('realtime', 'fcm')),
    status          text        not null check (status in ('sent', 'failed', 'skipped')),
    detail          text,
    device_id       uuid references notification_devices (id) on delete set null,
    created_at      timestamptz not null default now()
);

create index idx_notification_deliveries_created on notification_deliveries (created_at desc);

alter table notifications enable row level security;
alter table notification_devices enable row level security;
alter table notification_preferences enable row level security;
alter table integration_settings enable row level security;
alter table notification_deliveries enable row level security;


create table face_audit_log
(
    id             uuid primary key     default gen_random_uuid(),
    event          face_event  not null,
    actor_id       uuid        references users (id) on delete set null,
    subject_id     uuid        references users (id) on delete set null,
    matched        boolean,
    confidence     real,
    faces_detected integer,
    detail         text,
    created_at     timestamptz not null default now()
);

create index idx_face_audit_created on face_audit_log (created_at desc);
create index idx_face_audit_actor on face_audit_log (actor_id, created_at desc);
create index idx_face_audit_subject on face_audit_log (subject_id, created_at desc);

alter table face_audit_log enable row level security;
