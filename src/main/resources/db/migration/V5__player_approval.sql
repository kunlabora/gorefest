-- Accounts need the admin's approval before they can use the app. Everyone already in is grandfathered.
alter table players
    add column approved_at timestamptz;
update players
set approved_at = created_at;
