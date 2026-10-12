# BUG-023: The privacy notice always says a Discord channel gets messages, even when no webhook is configured

- **Severity:** cosmetic (wrong claim, only in setups without `NOTIFY_DISCORD_WEBHOOK`)
- **Area:** `/privacy`, `PrivacyViews.kt` ("Who else handles it")

## Steps to reproduce
1. Run without `NOTIFY_DISCORD_WEBHOOK` (the QA setup; also production if the admin prefers mail).
2. Open `/privacy`.

## Expected
G1: "The contact the notice shows follows the admin notifier: Discord wording with a webhook, the `ADMIN_EMAIL` address without". The contact paragraph does follow it, but the list under "Who else handles it" does not.

## Actual
The list always has: "A Discord channel only I can read gets a short message when someone signs up or asks to have their data or account handled ..." although with no webhook the admin is notified by mail (verified: the mails "Anna wants into Boardfest", "Bram wants to get their data" went to the admin's mailbox). The notice then names a processor that is not used. Related: with no mail provider the line reads "no mail provider (this is a development copy; mail stays on this machine), sends the mail." (dev only).

## Suspected cause (guess)
`PrivacyView` carries `contactEmail` and `mailProvider` but not "is a webhook set"; the Discord `li` is unconditional.

## Status
Status: fixed.
