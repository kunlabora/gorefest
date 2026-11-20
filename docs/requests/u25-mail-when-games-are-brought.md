# U25 Mail the others when someone brings games

Source: Tim, 2026-11-20. Status: 📋 todo

**Idea:** when someone decides to bring a game (or several) to a game day, the other players on that game day get a
mail saying so, so they know what's on the shelf without opening the app, and can wish for those games or compare them in the wizard in time.

**Earlier:** U19 (2026-10-06) asked the same and was set to won't do on 2026-10-14. Its estimate still holds: even
one mail per change is a few thousand mails a year at worst, well under a euro; a digest is a few hundred. This story
picks it back up.

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
