# BUG-011: CSS and JS are re-downloaded on every page view; HEAD requests to them 404

- **Severity:** minor
- **Area:** Static serving (`Server.kt`)
- **Environment:** any; checked with curl and the Browser pane's network log.

## Steps to reproduce
1. `curl -s -D - -o /dev/null http://localhost:8099/static/css/gorefest.css`
2. Navigate between a few pages with the network panel open (or `read_network_requests`).
3. `curl -I http://localhost:8099/static/css/gorefest.css` and `curl -I http://localhost:8099/`.

## Expected
Validators (`ETag` / `Last-Modified`) or a short `Cache-Control`, so a revisit is a 304 or a cache hit; HEAD answered like GET (uptime monitors and link previewers use it).

## Actual
- `gorefest.css` (59 KB), `tokens.css`, `theme.js`, `htmx.min.js`, `_hyperscript.min.js` come back `200` with only `Content-Length`/`Content-Type`, on every navigation. Only `.woff2` gets `Cache-Control`.
- `HEAD /static/css/gorefest.css` is `404`, `HEAD /` is `405` (GET works).

## Evidence
```
HTTP/1.1 200 OK / Content-Length: 59118 / Content-Type: text/css   (no Cache-Control, ETag or Last-Modified)
curl -I .../static/css/gorefest.css  -> HTTP/1.1 404 Not Found
curl -I .../                         -> HTTP/1.1 405 Method Not Allowed
```

## Suspected cause (guess)
`Server.kt` `staticResources("/static", "static") { modify { ... } }` only adds a header for `.woff2`; the Ktor `CachingHeaders`/`ConditionalHeaders` plugins are not installed and `AutoHeadResponse` is missing.

## Status

**Status: fixed.** Static assets now carry a SHA-256 ETag and `Cache-Control: no-cache` (fonts keep their immutable year), and HEAD is answered like GET via `AutoHeadResponse` and `ConditionalHeaders`.
