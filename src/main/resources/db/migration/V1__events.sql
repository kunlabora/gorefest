create table events
(
    seq  bigserial primary key,
    type text        not null,
    tags text[]      not null,
    data jsonb       not null,
    at   timestamptz not null default now()
);

create index events_tags on events using gin (tags);
create index events_type on events (type);
