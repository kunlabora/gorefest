-- Accounts nobody signs in to are erased after a warning (INACTIVE_ACCOUNT_MONTHS). Everyone starts from now.
alter table players
    add column last_login_at     timestamptz not null default now(),
    add column inactive_warned_at timestamptz;
