# BUG-015: POSTing without a form body (or with JSON) answers HTTP 500 instead of 400/415

- **Severity:** minor
- **Area:** Web layer, every route that calls `call.receiveParameters()` (public `/login` included)
- **Environment:** curl against http://localhost:8099; no browser needed. Reproduced on `/login` (not logged in), `/gamedays`, `/polls`, `/welcome`, `/gamedays/{id}/taste`, `/gamedays/{id}/log`.

## Steps to reproduce
1. `curl -i -X POST http://localhost:8099/login` (no body, no content type).
2. `curl -i -H 'Content-Type: application/json' -d '{"email":"a@b.co"}' http://localhost:8099/login`.
3. Same against the other routes above with a valid session cookie.

## Expected
`400 Bad Request` or `415 Unsupported Media Type`, logged at most at WARN. A malformed request is the client's problem.

## Actual
`HTTP/1.1 500 Internal Server Error`, body `Something went wrong`, and the generic handler logs an ERROR with a stack trace for every such request. Anyone, without logging in, can trigger it on `/login`, which adds noise to error alerting.

## Evidence
```
HTTP/1.1 500 Internal Server Error
Content-Length: 20
Content-Type: text/plain; charset=UTF-8

Something went wrong
```

## Suspected cause (guess)
`Server.kt` installs `StatusPages { exception<Throwable> { ... 500 } }`. The server log shows each probe as `ERROR gorefest.web - Unhandled error on /login` with `io.ktor.server.plugins.CannotTransformContentToTypeException: Cannot transform this request's content to io.ktor.http.Parameters`. Map that exception (and `BadRequestException`/`UnsupportedMediaTypeException`) to 400/415 before the `Throwable` handler.

## Status

Status: fixed. `StatusPages` maps `UnsupportedMediaTypeException` to 415 and `ContentTransformationException`/`BadRequestException` to 400, without the ERROR stack trace. Commit: see the "BUG-006, 007, 015, 019" commit on this branch.
