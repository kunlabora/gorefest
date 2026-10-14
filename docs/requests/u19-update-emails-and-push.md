# U19 Update emails when people add games (and push as an alternative)

Source: user testing, 2026-10-06. Status: 🚫

**Feedback:** Email when people have added their games to a game day, to get updates? What could be a good guesstimate (our last Boardfest had 9 people, we organise it about 4 times a year)? If too expensive, is push with another service cheaper?

**Decision (2026-10-06):** record only, with the estimate.

**Estimate**
- Volume: 9 players x ~4 events a year. If every shelf change mailed everyone: say 9 players x 15 changes x 8 people = about 1,000 mails per event, 4,000 a year, at worst. With a daily digest per game day (max one mail a day per player per game day, only when the shelf changed): about 9 players x ~7 days = 60 mails per event, 250 a year.
- Cost (check current price lists before deciding): Scaleway TEM is priced per 1,000 mails, roughly in the order of cents per thousand to a few euro cents; Postmark/Resend free tiers cover 100/day or 3,000/month. Either way 250 to 4,000 mails a year is rounding error: well under a euro a year.
- Web push (VAPID, no third party needed, the browser vendor delivers it) costs nothing per message, but needs a service worker, an install-to-home-screen on iOS, and permission prompts; our "no SPA, no offline" stance makes email the simpler fit.
- Recommendation: daily digest mail, opt-out per player, when we build it.

**Open questions:** digest time of day; one mail per game day or one overall; a per-player opt-out setting (plain table, not an event).

**Decision (2026-10-14):** won't do.
