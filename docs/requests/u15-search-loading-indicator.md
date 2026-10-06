# U15 Loading indicator during active search

Source: user testing, 2026-10-06. Status: 📋

**Feedback:** No loading indicator during an active search; sometimes this is useful.

**Wanted:** the search fields (bring a game by search, wizard anchors if any) show a subtle indicator while the request is in flight (`htmx-indicator`), including the slow path where BGG is queried.

**Criteria**
- [ ] Indicator component in `web/components`, themed
- [ ] Used on every live search
