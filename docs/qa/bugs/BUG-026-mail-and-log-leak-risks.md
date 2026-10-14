# BUG-026: Two ways server logs can still carry an email address or a code (one verified in dev only, one read from the code)

- **Severity:** minor (G8 promises "no links or email addresses" in logs, and the privacy notice repeats it)
- **Area:** `shell/auth/Mailer.kt`, `shell/auth/HttpMailers.kt`, `shell/web/Server.kt`

## Findings
1. Verified: in dev mode `DevMailbox` logs `Mail to tim@example.com: Your Boardfest code: 537620` at INFO for every mail (address and the login code). Dev only (`GOREFEST_ENV` dev uses `DevMailbox`), so harmless in production, but the "grep the log" check fails in a dev run and the dev log is a place real addresses would end up if someone runs a copy with real data.
2. Not verified (needs a real provider): `HttpMailer` throws `check(...) { "Mail provider answered ${status}: ${body.take(200)}" }`. Provider error bodies often echo the offending recipient (e.g. an invalid or suppressed address). That exception travels to `StatusPages` `log.error("Unhandled error on <uri>", cause)` (or `Background job failed`), so the message, with the address, lands in the log with a stack trace. Suggest logging only the status code.

## Everything else checked is clean
Over a full run (about 450 requests, logins by code and by link, invites, polls, og.png, bad tokens, query strings) the access log contained only `METHOD /path` with `/i/{token}`, `/p/{token}`, no query strings, no `/login/link` token, no tokens or codes outside the `DevMailbox` lines, and no WARN/ERROR lines.

## Status
Status: fixed. `DevMailbox` logs only that a mail arrived; a refused mail's message is "Mail provider answered <status>"; while at it, BGG sync logs name the player id instead of the BGG username, and a BGG error names the endpoint without its query (which can hold a username). Pinned by `LogSafetyTest` and `HttpMailerTest`.
