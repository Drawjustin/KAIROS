create table if not exists context_search_log (
    id bigserial primary key,
    user_id bigint not null references users(id),
    project_id bigint not null references project(id),
    purpose varchar(50) not null,
    query varchar(2000) not null,
    requested_context_source_ids varchar(1000) null,
    searched_context_source_ids varchar(1000) null,
    result_count integer not null default 0,
    status varchar(50) not null,
    latency_ms bigint not null,
    error_code varchar(50) null,
    trace_id varchar(64) null,
    created_at timestamp with time zone not null default current_timestamp,
    constraint ck_context_search_log_result_count_non_negative check (result_count >= 0),
    constraint ck_context_search_log_latency_ms_non_negative check (latency_ms >= 0)
);

create index if not exists idx_context_search_log_project_created_at
    on context_search_log(project_id, created_at desc);

create index if not exists idx_context_search_log_user_created_at
    on context_search_log(user_id, created_at desc);

create index if not exists idx_context_search_log_status_created_at
    on context_search_log(status, created_at desc);
