alter table users
    add column if not exists deleted_at timestamp with time zone null;

alter table refresh_session
    add column if not exists deleted_at timestamp with time zone null;
