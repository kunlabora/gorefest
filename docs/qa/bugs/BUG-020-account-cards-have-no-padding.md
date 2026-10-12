# BUG-020: The "Send me my data" and "Delete my account" cards on /account have no inner padding

- **Severity:** minor
- **Area:** `/account` (`AccountViews.kt`, `request()` builds `section("gf-card gf-account-request")`)
- **Environment:** Browser pane, desktop light and mobile 375px dark (same on both).

## Steps to reproduce
1. Log in, open `/account`, scroll to "Your data".

## Expected
Cards like the rest of the app: the text and the button sit inside the card with padding, and two cards have a gap between them.

## Actual
The heading, text, notice and button touch the card's border (the primary button even overlaps the rounded corner), and the two cards touch each other with no gap. The "Asked. I'll get back to you." notice, once shown, would sit flush the same way.

## Evidence
Screenshots: `../screenshots/BUG-020-account-cards-have-no-padding-mobile-dark.png`, `../screenshots/BUG-020-account-cards-have-no-padding-desktop-light.png`.
`docs/design/css/gorefest.css` line 393: `.gf-card { background; border; border-radius; box-shadow }` has no `padding`, and there is no `.gf-account-request` rule anywhere (`grep -rn gf-account-request` finds only the Kotlin).

## Suspected cause (guess)
`gf-card` is only a frame; other screens put an inner element with padding inside it. `request()` puts the content straight into the section and the per-screen CSS block for the account page was never written (CLAUDE.md: screen CSS lives in per-screen blocks).

## Status
Status: fixed.
