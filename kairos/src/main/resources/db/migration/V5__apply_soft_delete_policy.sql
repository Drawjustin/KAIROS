alter table users
    drop constraint if exists users_email_key;

create unique index if not exists ux_users_email_active
    on users(email)
    where deleted_at is null;

create index if not exists idx_users_deleted_at
    on users(deleted_at);

create index if not exists idx_refresh_session_deleted_at
    on refresh_session(deleted_at);
