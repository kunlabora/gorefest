-- BGG data we cache. Plain tables: BGG owns this data, it churns, and it's not a decision anyone made here.

create table games
(
    bgg_id     int primary key,
    data       jsonb       not null,
    fetched_at timestamptz not null
);

create table collections
(
    player_id uuid not null references players on delete cascade,
    bgg_id    int  not null,
    primary key (player_id, bgg_id)
);

create table collection_syncs
(
    player_id  uuid primary key references players on delete cascade,
    username   text        not null,
    status     text        not null, -- queued, done, failed
    message    text,
    updated_at timestamptz not null,
    synced_at  timestamptz
);
