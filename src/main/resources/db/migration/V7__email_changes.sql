-- A player proving they own a new address before it replaces the old one. One pending change per player; the code is hashed.
create table email_changes
(
    player_id   uuid primary key references players on delete cascade,
    new_email   text        not null,
    code_hash   bytea       not null,
    expires_at  timestamptz not null,
    used_at     timestamptz,
    wrong_codes int         not null default 0,
    created_at  timestamptz not null
);
