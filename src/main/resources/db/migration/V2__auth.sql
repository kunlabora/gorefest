-- Plain tables, not events: personal data must be erasable, and tokens/sessions are operational state.

create table players
(
    id         uuid primary key,
    email      text        not null unique,
    name       text,
    created_at timestamptz not null default now()
);

create table login_attempts
(
    id          uuid primary key,
    email       text        not null,
    token_hash  bytea       not null unique,
    code_hash   bytea       not null,
    return_to   text        not null,
    expires_at  timestamptz not null,
    used_at     timestamptz,
    wrong_codes int         not null default 0,
    created_at  timestamptz not null default now()
);

create table sessions
(
    token_hash bytea primary key,
    player_id  uuid        not null references players on delete cascade,
    expires_at timestamptz not null,
    created_at timestamptz not null default now()
);

create index sessions_player on sessions (player_id);
