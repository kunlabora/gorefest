# G1 Privacy notice and processing register

Source: GDPR analysis, 2026-10-09 ([`docs/gdpr.md`](../gdpr.md)). Status: 📋

**Why:** Art. 13: people must be told what we keep before they give us an email. See finding 3 in [`../gdpr.md`](../gdpr.md).

**Wanted:** a `/privacy` page, linked from the footer of every page (`page()`) and from the sign-in screen, in the brand book's plain voice (`docs/design/`). Public (no login), still `noindex`. Also `docs/processing-register.md`, the internal Art. 30 record.

**Content:** who runs it (name + contact email from config, `PRIVACY_CONTACT`, falls back to the host's mail address in dev); what we keep (the inventory table in `docs/gdpr.md`) and why; who sees what (members of the same game day see your name, shelf, wishes; co-members see your collection on the ask screen); processors (Scaleway, mail provider, BGG as separate controller); cookies (`gf_session`, `gf_theme`); retention (as set by G7; until then say "until you delete your account"); rights (access: G4, erasure: G3, rectification: G6) and the right to complain to the Gegevensbeschermingsautoriteit.

**Criteria**
- [ ] `GET /privacy` renders without a session; web test pins the main sections and that the footer link exists on a normal page and on the sign-in page
- [ ] Contact comes from `AppConfig`, tested with a fake value
- [ ] `docs/processing-register.md`: purpose, categories, recipients, transfers, retention, security measures; one row per data store in the inventory
- [ ] The notice names the mail provider in use (`MAIL_PROVIDER`), not a fixed one
- [ ] Tick G1 in the backlog

**Not in scope:** a cookie banner (nothing needs consent).
