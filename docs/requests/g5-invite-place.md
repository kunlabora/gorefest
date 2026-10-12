# G5 Don't show the place to non-members

Source: GDPR analysis, 2026-10-09 ([`docs/gdpr.md`](../gdpr.md)). Status: ✅

**Why:** finding 4. `/i/{token}` and `/i/{token}/og.png` show `place` to anyone holding the link, and the OG image is publicly cached and fetched by link-preview bots. A home address leaves the group.

**Wanted:** the invite landing page, its Open Graph tags and the OG image show title, date, host name, number going and shelf size, but not the place, until the visitor has joined (members see it on the hub already). If the product wants a hint, show only what the host marks as public: out of scope; keep it simple and drop the place. Check CLAUDE.md's journey text ("Open Graph preview: title, date, place, shelf size") and update it, as well as `docs/design` mockups if they show a place on the landing.

**Criteria**
- [x] Web test: landing page HTML, `og:*` meta tags and the OG image's input (`InvitePreview`) contain no place for a non-member or a logged-out visitor
- [x] Members still see the place on the hub
- [x] Date-poll invite (`/p/{token}`) checked for the same leak and fixed the same way
- [x] CLAUDE.md and the backlog's S6 wording updated
