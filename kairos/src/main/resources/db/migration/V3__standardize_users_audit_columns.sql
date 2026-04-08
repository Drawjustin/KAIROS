alter table users
    alter column created_at type timestamp with time zone
        using created_at at time zone current_setting('TIMEZONE'),
    alter column updated_at type timestamp with time zone
        using updated_at at time zone current_setting('TIMEZONE');

alter table users
    alter column created_at set default current_timestamp,
    alter column updated_at set default current_timestamp;