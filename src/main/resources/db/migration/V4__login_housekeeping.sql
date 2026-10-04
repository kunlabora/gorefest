-- Rate limiting counts attempts per email over the last hour; the purge deletes by age.
create index login_attempts_email_created on login_attempts (email, created_at);
create index login_attempts_created on login_attempts (created_at);
create index sessions_expires on sessions (expires_at);
