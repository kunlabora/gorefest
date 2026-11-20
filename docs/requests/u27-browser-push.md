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
2. Four notifications, all for game days still to be played, never to the one who did it:

   | When                                          | Event              | Who gets it                                   | Opens            |
   |-----------------------------------------------|--------------------|-----------------------------------------------|------------------|
   | Someone brings games                          | `GameBrought`      | everyone else who joined (batched as in U19)  | the shelf        |
   | Someone joins the game day                    | `PlayerJoined`     | everyone already in, host included            | the game day     |
   | The host cancels the game day                 | `GameDayCancelled` | everyone who joined                           | the game day     |
   | Someone asks you to bring a game (U20)        | `BringRequested`   | the owner asked                               | the bring page   |

   When games brought answer a bring request, the asker's notification says so ("Tim brings Cascadia, as you asked").
3. Tapping a notification opens the page it's about (the table above).
4. Each device is its own subscription. A player can switch a device off, and a subscription the browser reports gone
   (404/410 from the push service) is deleted.
5. Push comes next to the mail, not instead of it: no choice between the two. A player with push on still gets U19's
   mail, unless they turned that mail off.
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
- One notifier in the shell follows the log (after the append, like the recap), decides who hears about what from the
  read models (`gameDaysById`, `bringRequests`), and sends the mail (U19) and the push from that one place. Display
  text lives in a pure shell value, like `Recap`.

**Decisions (2026-11-20):** push next to mail, not a choice between them. The four notifications: game brought,
player joined, game day cancelled, game requested. The home-screen step on iPhone is acceptable.
A cancelled game day also sends a mail to everyone who joined; joining and bring requests are push only, for now.

## Open questions

- Can a player turn off one kind (say, "someone joined") and keep the rest, or is it all or nothing per device?

## Privacy

New processing: push subscriptions (device endpoint URL and keys) in a plain table, and the browser maker's push
service as a recipient of encrypted payloads. Needs lines in `docs/gdpr.md`, `docs/processing-register.md` and the
`/privacy` notice. Run `privacy-check` when done.
