# U15 Loading indicator during active search

Source: user testing, 2026-10-06. Status: ✅

**Feedback:** No loading indicator during an active search; sometimes this is useful.

**Wanted:** the search fields (bring a game by search, wizard anchors if any) show a subtle indicator while the request is in flight (`htmx-indicator`), including the slow path where BGG is queried.

**Criteria**
- [x] Indicator component in `web/components`, themed
- [x] Used on every live search

**Done:** `searchField` (bring, checkpoint log: the only live searches; the wizard has none) has a `.gf-spin.htmx-indicator` at the field's end, driven by `hx-indicator="closest .gf-search"`, so it also shows during the slow BGG path.
