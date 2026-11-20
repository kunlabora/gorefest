# U27 Browser push notifications

Source: Tim, 2026-11-20. Status: 📋 todo

**Idea:** besides mail, a player can turn on browser push notifications. These are short notices the phone or browser
shows even when Boardfest isn't open. U19's estimate (2026-10-06) called this "push as an alternative" to update
mails. U19 is now mail only, so push is its own story.

**Today:** Boardfest only reaches people by mail (login, recap, account mails, and U19's mail when games are brought).
No web app manifest, no service worker, no notification permission. The Content-Security-Policy (`Server.kt`) already
allows a service worker from our own origin (`script-src 'self'`, `connect-src 'self'`).

## Behaviour (draft)

1. On their account page (or after joining a game day), a player can switch on notifications on this device. The
   browser's permission prompt only appears after they tap that switch, never on page load.
2. The first notifications are the ones U19 mails: someone brings games to a game day you joined. Later candidates are
   a bring request for you (U20), a game day's date or place changing, and the recap.
3. Tapping a notification opens the page it's about (the game day's shelf).
4. Each device is its own subscription. A player can switch a device off, and a subscription the browser reports gone
   (404/410 from the push service) is deleted.
5. Push comes next to the mail, not instead of it, unless the player chooses push only (open question).
6. On iPhone, push only works once Boardfest is added to the home screen (iOS 16.4+). The switch says so on iOS instead
   of failing silently.

## Design notes

- Web Push with VAPID keys (`VAPID_PUBLIC_KEY`/`VAPID_PRIVATE_KEY`, like `SECRET_KEY`), sent from the shell through a
  small `PushSender` next to `Mailer`, with a fake for dev and tests. Needs a Java library for the payload encryption
  (e.g. `nl.martijndwars:web-push`), or a hand-rolled one (ECDH + HKDF + AES-GCM, RFC 8291).
- A service worker (`/sw.js`, served from the root so its scope is the whole site) and a web app manifest (name,
  icons, colours from `tokens.json`) for the iOS home-screen requirement.
- Subscriptions (endpoint, keys, player id, created at) go in a plain table, not events. They are device data, and they
  cascade with the account on erasure.
- The payload holds as little as possible: a title, a line and a URL, no email or BGG username. It passes through the
  browser maker's push service (Google, Apple, Mozilla), encrypted end to end.
- Whatever sends U19's mail decides who hears about it, and both mail and push go out from that one place.

## Open questions

- Push next to mail, or a per-player choice (mail / push / both)? And is one opt-out for "games brought" enough?
- Which events are worth a push from the start? A push about every shelf change may feel too noisy; batching like U19?
- Is the home-screen step on iPhone acceptable for the group, or does it make push Android/desktop only in practice?

## Privacy

New processing: push subscriptions (device endpoint URL and keys) in a plain table, and the browser maker's push
service as a recipient of encrypted payloads. Needs lines in `docs/gdpr.md`, `docs/processing-register.md` and the
`/privacy` notice. Run `privacy-check` when done.
