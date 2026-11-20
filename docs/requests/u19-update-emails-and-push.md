# U19 Update emails when people add games (and push as an alternative)

Source: user testing, 2026-10-06. Status: 📋 todo

**Feedback:** Email when people have added their games to a game day, to get updates? What could be a good guesstimate (our last Boardfest had 9 people, we organise it about 4 times a year)? If too expensive, is push with another service cheaper?

**Decision (2026-10-06):** record only, with the estimate.

**Estimate**
- Volume: 9 players x ~4 events a year. If every shelf change mailed everyone: say 9 players x 15 changes x 8 people = about 1,000 mails per event, 4,000 a year, at worst. With a daily digest per game day (max one mail a day per player per game day, only when the shelf changed): about 9 players x ~7 days = 60 mails per event, 250 a year.
- Cost (check current price lists before deciding): Scaleway TEM is priced per 1,000 mails, roughly in the order of cents per thousand to a few euro cents; Postmark/Resend free tiers cover 100/day or 3,000/month. Either way 250 to 4,000 mails a year is rounding error: well under a euro a year.
- Web push (VAPID, no third party needed, the browser vendor delivers it) costs nothing per message, but needs a service worker, an install-to-home-screen on iOS, and permission prompts; our "no SPA, no offline" stance makes email the simpler fit.
- Recommendation: daily digest mail, opt-out per player, when we build it.

**Open questions:** digest time of day; one mail per game day or one overall; a per-player opt-out setting (plain table, not an event).

**Decision (2026-10-14):** won't do.

**Decision (2026-11-20):** will do after all, as a mail to the others when someone decides to bring a game or games.
The estimate above still holds. Push stays out.

**Today:** bringing a game appends `GameBrought` (and `GameUnbrought` when taken back); a bring request is answered by
the owner's `GameBrought` (U20). Nobody hears about it until they open the game day. The only mails to players are the
login code, email change, account requests, the inactive-account warning and the recap (`RecapMailer`), all plain
text through `Mailer`.

## Behaviour (draft)

1. When a player brings one or more games to a game day still to be played, everyone else who joined it gets a mail:
   who brings what ("Anna brings Brass: Birmingham and Cascadia to Gorefest #7"), with a link to the game day's shelf.
2. Several games brought close together go in one mail, not one per game (bringing usually happens as a batch of
   ticks on the bring page).
3. A game brought and taken back before the mail goes out isn't mentioned.
4. The one who brought the games gets no mail about their own.
5. Nothing for a cancelled or ended game day.
6. A player can turn these mails off (open question: per game day, or for all).
7. When a bring request is answered by bringing the game, the asker's mail says so ("Tim brings Cascadia, as you
   asked").

## Open questions

- Batching: a short delay per bringer and game day (say 10–15 minutes after the last tick), or U19's daily digest per
  player and game day? The delay is closer to "someone decided"; the digest sends fewer mails.
- Opt-out: where (the collection or account page, or the game day), and is it a plain-table setting (U19 suggested
  so) or an event? It's a preference about mail, not something the matcher needs, so a plain table fits.
- The host too, or only the other players? (Today: everyone joined, host included.)
- Should taking a game back (`GameUnbrought`) after a mail went out send anything? Probably not.
- Restarts: a pending batch lives in memory; is losing one on a deploy acceptable, or does it need a table?

## Privacy

The mail names other players and games to people on the same game day, who already see that on the shelf. Mail
content goes to the mail provider (processor, already in `docs/gdpr.md`); the opt-out and any queue need lines in the
processing register. Run `privacy-check` when done.
