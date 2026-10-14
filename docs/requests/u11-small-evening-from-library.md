# U11 Organise a small game evening from people's libraries

Source: user testing, 2026-10-06. Status: ✅

**Feedback:** A feature to organise a game evening with fewer people, where it is more "I see these games in your library and want to play game a, b and c".

**Decision (2026-10-06):** record only; shape together before any code.

**Sketch:** the host invites a few friends; sees the union of their linked collections; proposes games a, b, c; friends say yes/maybe/no; the game day is created with those as the intended shelf. Related: date poll (S17), inverse matching ("who'd play my game?") in CLAUDE.md's Later list.

**Open questions:** do collections need to be linked for everyone? Does it replace or extend a game day? Do proposals count as wishes (U10)?

**Decision (2026-10-14):** a separate concept next to the game day, not a replacement and not an extension. It uses the same matching rules.

**Decided (2026-10-14):** a friend's "yes" to a proposed game is a heart: it counts as a wish (U10) for the matcher, with the same limit of 3 per person.

**Picked (2026-10-14): option C, "date and games poll"** (see [`u11-options.html`](u11-options.html)), with these additions and answers:
- The host can add any game by BGG search, not only from someone's collection (e.g. Humanity, which Ruben has but nobody linked).
- Anyone in the poll can offer to bring a proposed game, collection or not. Joining friends who have a proposed game in their linked collection are asked to bring it.
- "Nobody can bring it" is per date: a game only counts on a date if someone who offered to bring it can come that day.
- Only the host adds games.
- The game night made from the pick is small in this first version: who's coming, the plan and who brings what, and "pick a game for my table". No checkpoints, play log, closing at midnight or recap mail yet.
- No "maybe" (it matches the yes/no of the dates). "No" is shown to the host only; the matcher ignores it.
- Collections don't have to be linked: without one you can still answer and offer to bring games.

## Criteria

**The poll asks about games**
- [x] When starting a date poll, the host can switch on "Ask about games as well" (`DatePollCreated.asksAboutGames`). Without it, the poll works and makes a game day exactly as today.
- [x] The host proposes games while the poll is open: ticked on the poll form from their own collection (likely favourites first) or found by BGG search, and later on the poll page from the same two; friends' collections stay theirs (changed 2026-10-14) (`DatePollGameProposed`). The host can take one back (`DatePollGameWithdrawn`), which drops its answers and offers. Only the host adds or removes games; at most `MAX_POLL_GAMES` (12).
- [x] Everyone in the poll answers yes or no per game and can change it until the pick (`DatePollGameAnswered`). At most 3 yeses per person (`MAX_WISHES`): a fourth is refused with the reason.
- [x] Per game anyone in the poll can say "I'll bring it" and take it back, whether or not it's in their collection (`DatePollBringAnswered`).
- [x] Someone with a proposed game in their linked collection is asked once per game: "You have Humanity. Bring it if we play?", with "I'll bring it" and "Not this time" (both answer it).

**Results**
- [x] Each game card shows who said yes, who brings it, and "Nobody can bring it yet" when nobody offered.
- [x] Each date card shows which games would be missing that day and why ("Missing that day: Humanity (Ruben can't come); Azul (nobody offered to bring it)"). The host counts as coming on every date, as for a game day.
- [x] "No" answers are shown to the host only.

**The pick makes a game night**
- [x] Picking a date in a poll with games makes a game night (`DatePollPickedForGameNight`, `GameNightCreated`) with the date, title, place, time zone, the host and everyone who can come.
- [x] Its plan holds the games someone coming offered to bring, each with the first such offer; games nobody coming can bring are listed apart as missing (`DatePollSummary.planFor`, shared by the results and the pick).
- [x] Every yes from someone coming is a wish on that game for the night; yeses from people not coming are dropped.
- [x] The game night page (members only) shows the date, place, who's coming and what they bring, the plan, what's missing, and "Pick a game for my table": the same matching as a game day, with the plan as the shelf. It's on the home page too.
- [x] After the pick the poll links to the game night, as it does to a game day.
- [x] Erasure: an erased player leaves the game night (their yeses go, a game they were to bring becomes missing); the data export describes your own answers, offers and game nights, never the others on them (`PersonalData`).

**Not in this version:** checkpoints, play log, closing at midnight, the recap mail, editing or cancelling a game night, its own invite link, a taste round from the game night.
