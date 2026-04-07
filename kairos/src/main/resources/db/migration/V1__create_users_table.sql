-- 인증의 기준이 되는 사용자 기본 테이블이다.
create table if not exists users (
    id bigserial primary key,
    email varchar(255) not null unique,
    password varchar(255) not null,
    role varchar(50) not null default 'USER',
    created_at timestamp not null default current_timestamp,
    updated_at timestamp not null default current_timestamp
);
