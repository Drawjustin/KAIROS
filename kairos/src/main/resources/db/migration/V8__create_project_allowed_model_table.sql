create table if not exists project_allowed_model (
    id bigserial primary key,
    project_id bigint not null references project(id),
    model varchar(80) not null,
    created_at timestamp with time zone not null default current_timestamp,
    updated_at timestamp with time zone not null default current_timestamp,
    deleted_at timestamp with time zone null
);

create unique index if not exists ux_project_allowed_model_active
    on project_allowed_model(project_id, model)
    where deleted_at is null;

create index if not exists idx_project_allowed_model_project_id
    on project_allowed_model(project_id);

create index if not exists idx_project_allowed_model_deleted_at
    on project_allowed_model(deleted_at);

insert into project_allowed_model(project_id, model)
select p.id, model.name
from project p
cross join (
    values
        ('GPT_4O_MINI'),
        ('GPT_4O'),
        ('GEMINI_2_5_FLASH'),
        ('CLAUDE_OPUS_4_7'),
        ('CLAUDE_SONNET_4_6'),
        ('CLAUDE_HAIKU_4_5')
) as model(name)
where p.deleted_at is null
on conflict do nothing;
