# BUG-019: A validation error on a plain (non-htmx) form post returns a bare `<form>` fragment, not a page

- **Severity:** minor
- **Area:** Web layer (`respondFragment` on error paths: `/login`, `/gamedays`, `/polls`, bring/log forms, ...)
- **Environment:** curl, or a browser where htmx didn't run (JS blocked, webjar failed to load, an in-app browser quirk). The forms all carry `action=` and `method="post"`, so they submit normally in that case.

## Steps to reproduce
1. `curl -i -d "email=bad&returnTo=/" http://localhost:8099/login` (no `HX-Request` header).
2. Logged in: `curl -i -b tim.jar -d "title=&date=2026-12-01&place=x" http://localhost:8099/gamedays`.

## Expected
Per CLAUDE.md ("htmx: handlers answer htmx requests with a fragment and plain requests with a full page or redirect"): the full page with the error shown, so the browser renders something styled.

## Actual
`200 OK`, `Content-Type: text/html`, body is only `<form action="/login" method="post" class="gf-signin gf-field--invalid" ...>` with the error inside; no `<html>`, no stylesheet link, no app bar. A browser would show an unstyled bare form.

## Evidence
```
HTTP/1.1 200 OK
Content-Length: 1038
Content-Type: text/html; charset=UTF-8

<form action="/login" method="post" class="gf-signin gf-field--invalid" id="email-form" hx-post="/login" hx-swap="outerHTML">...
```

## Suspected cause (guess)
`web/Responses.kt` `respondFragment` never looks at the `HX-Request` header. Success paths use `redirect()` which does branch (302 vs `HX-Redirect`), the error paths do not.

## Status

Status: fixed. `respondFragment` wraps the fragment in a full styled page for a plain (non-htmx) form POST; htmx requests and GET fragments are unchanged. Commit: see the "BUG-006, 007, 015, 019" commit on this branch.
