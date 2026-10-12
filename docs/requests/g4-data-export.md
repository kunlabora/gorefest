# G4 Download my data

Source: GDPR analysis, 2026-10-09 ([`docs/gdpr.md`](../gdpr.md)). Status: ✅ (as the request-to-admin version)

**Why:** Art. 15 and 20. Finding 2.

**Decided 2026-10-12:** the button posts a request to the admin's Discord channel (same `AdminNotifier` as G3) instead of downloading at once; the admin sends the file. The `PersonalData` builder below is still needed, for the admin to run. An instant download can come later.

**Wanted:** on `/account` (create it if G3 hasn't landed; coordinate through the file `web/account/AccountRoutes.kt`), a "Download my data" button returning `gorefest-my-data.json` (`Content-Disposition: attachment`, `no-store`). Contents, with game names rather than bare ids: profile (name, email, created), BGG username and collection, game days I joined or hosted (title, date, place, my role), what I brought, my wishes, my comparisons and "haven't played" answers with timestamps, plays I'm in, my bring requests, my date-poll answers. Never other people's data: other players appear only as names already visible to me, or not at all.

**Criteria**
- [ ] Pure `PersonalData.of(player, projections…)` in `core` or a pure shell value, plus a serializer; unit tested
- [ ] Web test: logged-in player gets valid JSON with their data; another player's data (email, answers) isn't in it; logged-out gets a redirect
- [ ] Download requires a recent login (the session is enough for now; note it as a follow-up in the story if you disagree)
- [ ] Mentioned in the privacy notice

**Built 2026-10-12:** the "Send me my data" button on `/account` is G3's request; the admin gets it at `/admin/requests` and downloads the file with "Download their data" (`/admin/requests/{id}/export`, `Content-Disposition: attachment`, `no-store`, admin only). `PersonalData.of` (pure) builds it from `PersonalProfile`, the events tagged with the player and name lookups (`PersonalDataExport` gathers them): profile, BGG username, collection, and a history in words. Other people never appear: a bring request says "someone", a play says how many were at the table. Tests: `PersonalDataTest`, `AccountRoutesTest`. Not done: timestamps for events that don't carry their own (the log keeps `at` in the table, `StoredEvent` doesn't expose it).
