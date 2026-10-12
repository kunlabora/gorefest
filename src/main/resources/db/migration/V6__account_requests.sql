-- People ask the admin to delete their account or send them their data. A plain table, so it goes with the account.
create table account_requests
(
    player_id    uuid        not null references players on delete cascade,
    kind         text        not null check (kind in ('DELETE', 'EXPORT')),
    requested_at timestamptz not null,
    primary key (player_id, kind)
);
