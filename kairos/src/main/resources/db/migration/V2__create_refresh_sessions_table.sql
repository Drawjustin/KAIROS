create table if not exists refresh_session (
    id bigserial primary key,
    user_id bigint not null references users(id) on delete cascade,
    session_id varchar(100) not null unique,
    token_hash varchar(128) not null,
    expires_at timestamp with time zone not null,
    revoked_at timestamp with time zone null,
    last_used_at timestamp with time zone null,
    platform varchar(100) null,
    device varchar(255) null,
    ip_address varchar(100) null,
    user_agent varchar(1000) null,
    created_at timestamp with time zone not null default current_timestamp,
    updated_at timestamp with time zone not null default current_timestamp
);

create index if not exists idx_refresh_session_user_id on refresh_session(user_id);
create index if not exists idx_refresh_session_expires_at on refresh_session(expires_at);
