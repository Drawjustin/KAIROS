create table if not exists project_context_source (
    id bigserial primary key,
    project_id bigint not null references project(id),
    type varchar(50) not null,
    name varchar(120) not null,
    description varchar(500) null,
    uri varchar(1000) null,
    content text null,
    status varchar(50) not null default 'ACTIVE',
    created_at timestamp with time zone not null default current_timestamp,
    updated_at timestamp with time zone not null default current_timestamp,
    deleted_at timestamp with time zone null
);

create unique index if not exists ux_project_context_source_name_active
    on project_context_source(project_id, lower(name))
    where deleted_at is null;

create index if not exists idx_project_context_source_project_id
    on project_context_source(project_id);

create index if not exists idx_project_context_source_project_status
    on project_context_source(project_id, status)
    where deleted_at is null;

create index if not exists idx_project_context_source_deleted_at
    on project_context_source(deleted_at);
