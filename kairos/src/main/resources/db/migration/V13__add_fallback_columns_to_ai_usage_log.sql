-- 대체 provider로 넘어간 호출을 추적한다.
-- 어떤 모델을 요청했는데 실제로 어떤 모델이 응답했는지 남지 않으면
-- 장애 대응이 작동했는지, 얼마나 자주 작동했는지 사후에 알 수 없다.
alter table ai_usage_log
    add column if not exists is_fallback boolean not null default false;

alter table ai_usage_log
    add column if not exists fallback_from_model varchar(120) null;

create index if not exists idx_ai_usage_log_fallback_created_at
    on ai_usage_log(created_at desc)
    where is_fallback;
