alter table users
    add column if not exists deleted_at timestamp with time zone null;

alter table refresh_sessions
    add column if not exists deleted_at timestamp with time zone null;
