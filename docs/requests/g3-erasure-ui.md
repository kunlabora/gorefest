# G3 Erase my account: the screen

Source: GDPR analysis, 2026-10-09 ([`docs/gdpr.md`](../gdpr.md)). Status: 📋

**Why:** Art. 17 needs a way to ask. Depends on [G2](g2-erasure-event-log.md).

**Decided 2026-10-12:** the button doesn't delete by itself. It posts a request to the admin's Discord channel (`AdminNotifier`: name and link, no email address; mail as the fallback, like the sign-up notice) and tells the person it was sent. The admin runs the erasure after G2. A self-serve delete can replace this later.

**Wanted:** a `/account` page (linked from the site bar's player menu or the footer) with the name, email, and a "Delete my account" section. Deleting asks for confirmation by typing the word the page tells them (or re-requesting a login code, whichever is the existing pattern; prefer the code: it proves it is them), says plainly what happens ("your answers and profile are removed; game days you hosted stay for the others without your name"), then runs `EventErasure`, ends the session, sends one last mail to the address confirming it, and lands on a goodbye page.

**Criteria**
- [ ] The request goes through `AdminNotifier`; a web test asserts it was sent once (a second click doesn't spam) and shows the person a confirmation
- [ ] Routes under `web.account`, views split per CLAUDE.md (Routes/Views/components)
- [ ] Web tests with `TestApp`: page shows my data; wrong confirmation does nothing; right one erases, clears the cookie, and the old session no longer works; a second request is harmless
- [ ] The confirmation mail goes through the mailer interface and is visible in the dev mailbox
- [ ] A host is warned that their upcoming game days stay and what changes for the guests
- [ ] Copy follows the brand book's voice; page is mobile-first, theme toggle present
- [ ] Linked from the privacy notice ([G1](g1-privacy-notice.md))
