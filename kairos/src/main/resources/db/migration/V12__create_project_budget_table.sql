-- project별 호출/토큰 한도.
-- 한 project가 일 한도와 월 한도를 함께 둘 수 있도록 기간별로 row를 나눈다.
-- limit이 null이면 해당 항목은 무제한으로 본다.
--
-- consumed 값은 애플리케이션에서 읽고 비교한 뒤 쓰는 것이 아니라
-- 조건을 UPDATE 문 안에 넣어 DB의 행 단위 원자성에 판정을 맡긴다.
-- AI 호출은 수 초가 걸려 예산 행을 잡고 있을 수 없으므로 락을 쓰지 않는다.
create table if not exists project_budget (
    id bigserial primary key,
    project_id bigint not null references project(id),
    period varchar(20) not null,
    request_limit bigint null,
    token_limit bigint null,
    consumed_requests bigint not null default 0,
    consumed_tokens bigint not null default 0,
    period_started_at timestamp with time zone not null,
    created_at timestamp with time zone not null default current_timestamp,
    updated_at timestamp with time zone not null default current_timestamp,
    deleted_at timestamp with time zone null,
    constraint ck_project_budget_request_limit_non_negative check (request_limit is null or request_limit >= 0),
    constraint ck_project_budget_token_limit_non_negative check (token_limit is null or token_limit >= 0),
    constraint ck_project_budget_consumed_requests_non_negative check (consumed_requests >= 0),
    constraint ck_project_budget_consumed_tokens_non_negative check (consumed_tokens >= 0)
);

create unique index if not exists ux_project_budget_active
    on project_budget(project_id, period)
    where deleted_at is null;

create index if not exists idx_project_budget_project_id
    on project_budget(project_id);

create index if not exists idx_project_budget_deleted_at
    on project_budget(deleted_at);
