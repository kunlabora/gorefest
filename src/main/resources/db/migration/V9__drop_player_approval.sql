-- New accounts no longer wait for the admin's approval: everyone is in at once and the admin rejects strangers.
alter table players
    drop column approved_at;
