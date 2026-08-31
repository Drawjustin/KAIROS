-- project별 민감정보 처리 정책.
-- 기본값은 PiiType enum이 들고 있으므로 이 테이블은 "기본값과 다르게 운영할 때만" 채운다.
-- 테이블이 비어 있어도 게이트웨이는 안전한 기본 정책으로 동작한다.
create table if not exists project_pii_policy (
    id bigserial primary key,
    project_id bigint not null references project(id),
    pii_type varchar(60) not null,
    action varchar(20) not null,
    created_at timestamp with time zone not null default current_timestamp,
    updated_at timestamp with time zone not null default current_timestamp,
    deleted_at timestamp with time zone null
);

create unique index if not exists ux_project_pii_policy_active
    on project_pii_policy(project_id, pii_type)
    where deleted_at is null;

create index if not exists idx_project_pii_policy_project_id
    on project_pii_policy(project_id);

create index if not exists idx_project_pii_policy_deleted_at
    on project_pii_policy(deleted_at);

-- 민감정보 검출 감사 로그.
-- 검출된 값의 원문은 물론이고 일부 조각도 저장하지 않는다.
-- 감사 로그 자체가 유출 경로가 되면 통제의 의미가 없기 때문이다.
-- 요청 단위 추적은 trace_id로 ai_usage_log, context_search_log와 이어 붙여 수행한다.
create table if not exists pii_detection_log (
    id bigserial primary key,
    project_id bigint not null references project(id),
    source varchar(50) not null,
    pii_type varchar(60) not null,
    detected_count integer not null,
    action varchar(20) not null,
    trace_id varchar(64) null,
    created_at timestamp with time zone not null default current_timestamp,
    constraint ck_pii_detection_log_detected_count_positive check (detected_count > 0)
);

create index if not exists idx_pii_detection_log_project_created_at
    on pii_detection_log(project_id, created_at desc);

create index if not exists idx_pii_detection_log_pii_type_created_at
    on pii_detection_log(pii_type, created_at desc);

create index if not exists idx_pii_detection_log_action_created_at
    on pii_detection_log(action, created_at desc);
