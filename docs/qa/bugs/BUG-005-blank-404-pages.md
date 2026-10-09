# BUG-005: Every 404 is a bare, empty white page

- **Severity:** minor
- **Area:** Error handling / access control (all member-only routes)
- **Environment:** desktop, dark browser theme, logged-in player Tim. Same for Zed, a logged-in non-member.

## Steps to reproduce
1. Log in as any player.
2. Open `http://localhost:8099/gamedays/00000000-0000-0000-0000-000000000000` (also: a real game day id you are not a member of, a cancelled game day's `/bring` or `/taste`, `/polls/xyz`, `/nonexistent`).

## Expected
Nothing leaks (this part works: a non-member gets the same 404 as a missing id), but the person sees an on-brand page with the app bar and a way back ("That game day isn't here. Back to your game days.").

## Actual
HTTP 404 with `Content-Length: 0`: a blank white page, no app bar, no link, ignoring the dark theme. A person following a stale link (cancelled game day, a poll they weren't picked for, a game day they never joined) has no hint what happened or where to go.

## Evidence
![blank](../screenshots/BUG-005-blank-404.png)

```
curl -i -b tim.jar localhost:8099/gamedays/00000000-0000-0000-0000-000000000000
HTTP/1.1 404 Not Found   X-Robots-Tag: ...   Content-Length: 0
```
(The invite pages do have a friendly dead-link page, so the pattern exists.) The 500 handler is also a plain `Something went wrong` text.

## Suspected cause (guess)
Routes call `call.respond(HttpStatusCode.NotFound)` everywhere and `Server.kt` installs `StatusPages` only for `Throwable`; adding `status(HttpStatusCode.NotFound)` (and 500) handlers that render a `page(...)` would fix all of them in one place.
