# G1 Privacy notice and processing register

Source: GDPR analysis, 2026-10-09 ([`docs/gdpr.md`](../gdpr.md)). Status: ✅

**Why:** Art. 13: people must be told what we keep before they give us an email. See finding 3 in [`../gdpr.md`](../gdpr.md).

**Scope (decided 2026-10-12):** the household exemption applies (see `gdpr.md`), so this is the light version: no controller/processor wording, no legal name, no Art. 30 register, no line about the Gegevensbeschermingsautoriteit. It is a plain notice from "Tim" (a first name, as the person who runs it).

**Wanted:** a `/privacy` page, linked from the footer of every page (`page()`) and from the sign-in screen, in the brand book's plain voice (`docs/design/`). Public (no login), still `noindex`. Also `docs/processing-register.md`, a short internal list of the data stores (not an Art. 30 record: it is not required).

**Content:** who runs it (a first name) and how to reach them: the same Discord channel as the approvals (`NOTIFY_DISCORD_WEBHOOK`) through the "delete my account" and "send me my data" buttons ([G3](g3-erasure-ui.md), [G4](g4-data-export.md)), and the `ADMIN_EMAIL` address as the fallback when no webhook is set; no separate `PRIVACY_CONTACT` setting; what we keep (the inventory table in `docs/gdpr.md`) and why; who sees what (members of the same game day see your name, shelf, wishes; co-members see your collection on the ask screen); processors (Scaleway, the mail provider) and where the collection comes from (we read it from BGG for the username you link; BGG gets nothing from us); cookies (`gf_session`, `gf_theme`); retention (as set by G7; until then say "until you delete your account"); what you can ask for (your data: G4, delete your account: G3, correct it: G6), without legal framing.

**Criteria**
- [x] `GET /privacy` renders without a session; web test pins the main sections and that the footer link exists on a normal page and on the sign-in page
- [x] The contact the notice shows follows the admin notifier: Discord wording with a webhook, the `ADMIN_EMAIL` address without; tested with fake values
- [x] `docs/processing-register.md`: a short list, one row per data store in the inventory (what, why, who sees it, how long)
- [x] The notice names the mail provider in use (`MAIL_PROVIDER`), not a fixed one
- [x] The notice says server logs are kept 14 days and hold no links or email addresses (from [G8](g8-logs.md)); then tick G8's last criterion and mark G8 ✅
- [x] Tick G1 in the backlog

**Not in scope:** a cookie banner (nothing needs consent).
