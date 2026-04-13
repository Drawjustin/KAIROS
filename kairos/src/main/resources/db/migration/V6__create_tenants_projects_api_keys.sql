create table if not exists tenant (
    id bigserial primary key,
    name varchar(120) not null,
    status varchar(50) not null default 'ACTIVE',
    created_at timestamp with time zone not null default current_timestamp,
    updated_at timestamp with time zone not null default current_timestamp,
    deleted_at timestamp with time zone null
);

create unique index if not exists ux_tenant_name_active
    on tenant(lower(name))
    where deleted_at is null;

create index if not exists idx_tenant_deleted_at
    on tenant(deleted_at);

create table if not exists tenant_user (
    id bigserial primary key,
    tenant_id bigint not null references tenant(id),
    user_id bigint not null references users(id),
    role varchar(50) not null,
    created_at timestamp with time zone not null default current_timestamp,
    updated_at timestamp with time zone not null default current_timestamp,
    deleted_at timestamp with time zone null
);

create unique index if not exists ux_tenant_user_active
    on tenant_user(tenant_id, user_id)
    where deleted_at is null;

create index if not exists idx_tenant_user_tenant_id
    on tenant_user(tenant_id);

create index if not exists idx_tenant_user_user_id
    on tenant_user(user_id);

create index if not exists idx_tenant_user_deleted_at
    on tenant_user(deleted_at);

create table if not exists project (
    id bigserial primary key,
    tenant_id bigint not null references tenant(id),
    name varchar(120) not null,
    environment varchar(50) not null default 'OPER',
    status varchar(50) not null default 'ACTIVE',
    created_at timestamp with time zone not null default current_timestamp,
    updated_at timestamp with time zone not null default current_timestamp,
    deleted_at timestamp with time zone null
);

create unique index if not exists ux_project_tenant_name_active
    on project(tenant_id, lower(name))
    where deleted_at is null;

create index if not exists idx_project_tenant_id
    on project(tenant_id);

create index if not exists idx_project_deleted_at
    on project(deleted_at);

create table if not exists api_key (
    id bigserial primary key,
    project_id bigint not null references project(id),
    created_by_user_id bigint not null references users(id),
    name varchar(120) not null,
    key_prefix varchar(32) not null,
    key_hash varchar(128) not null,
    expires_at timestamp with time zone null,
    last_used_at timestamp with time zone null,
    revoked_at timestamp with time zone null,
    created_at timestamp with time zone not null default current_timestamp,
    updated_at timestamp with time zone not null default current_timestamp,
    deleted_at timestamp with time zone null
);

create unique index if not exists ux_api_key_project_name_active
    on api_key(project_id, lower(name))
    where deleted_at is null;

create unique index if not exists ux_api_key_key_hash_active
    on api_key(key_hash)
    where deleted_at is null;

create index if not exists idx_api_key_project_id
    on api_key(project_id);

create index if not exists idx_api_key_revoked_at
    on api_key(revoked_at);

create index if not exists idx_api_key_deleted_at
    on api_key(deleted_at);
