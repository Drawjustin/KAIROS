-- refresh token의 서버 측 상태와 디바이스 메타데이터를 관리한다.
create table if not exists refresh_sessions (
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

-- 사용자 기준 정리와 만료 정리에 자주 쓰는 컬럼에 인덱스를 둔다.
create index if not exists idx_refresh_sessions_user_id on refresh_sessions(user_id);
create index if not exists idx_refresh_sessions_expires_at on refresh_sessions(expires_at);
