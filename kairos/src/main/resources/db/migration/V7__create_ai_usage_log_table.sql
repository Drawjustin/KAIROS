create table if not exists ai_usage_log (
    id bigserial primary key,
    project_id bigint not null references project(id),
    api_key_id bigint not null references api_key(id),
    provider varchar(50) not null,
    model varchar(120) not null,
    input_tokens integer not null default 0,
    output_tokens integer not null default 0,
    total_tokens integer not null default 0,
    status varchar(50) not null,
    latency_ms bigint not null,
    error_code varchar(50) null,
    provider_response_id varchar(160) null,
    trace_id varchar(64) null,
    created_at timestamp with time zone not null default current_timestamp,
    constraint ck_ai_usage_log_input_tokens_non_negative check (input_tokens >= 0),
    constraint ck_ai_usage_log_output_tokens_non_negative check (output_tokens >= 0),
    constraint ck_ai_usage_log_total_tokens_non_negative check (total_tokens >= 0),
    constraint ck_ai_usage_log_latency_ms_non_negative check (latency_ms >= 0)
);

create index if not exists idx_ai_usage_log_project_created_at
    on ai_usage_log(project_id, created_at desc);

create index if not exists idx_ai_usage_log_api_key_created_at
    on ai_usage_log(api_key_id, created_at desc);

create index if not exists idx_ai_usage_log_provider_model_created_at
    on ai_usage_log(provider, model, created_at desc);

create index if not exists idx_ai_usage_log_status_created_at
    on ai_usage_log(status, created_at desc);
