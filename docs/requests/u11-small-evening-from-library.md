# U11 Organise a small game evening from people's libraries

Source: user testing, 2026-10-06. Status: ⏭

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
- [ ] When starting a date poll, the host can switch on "Ask about games as well". Without it, the poll works and makes a game day exactly as today.
- [ ] The host proposes games for the poll while it is open: from their own collection, from the collections of people who joined, or by BGG search for any game (`DatePollGameProposed`). The host can take a proposal back (`DatePollGameWithdrawn`). Only the host adds or removes games.
- [ ] On the poll page everyone answers yes or no per proposed game, next to the dates, and can change it until the pick (`DatePollGameAnswered`). At most 3 yeses per person (`MAX_WISHES`): a fourth is refused with the reason.
- [ ] Per proposed game anyone in the poll can say "I'll bring it" and take it back (`DatePollBringOffered`, `DatePollBringWithdrawn`), whether or not it's in their collection.
- [ ] When someone joins, or opens the poll, and a proposed game is in their linked collection, they're asked once per game: "You have Humanity. Bring it if we play?", with "I'll bring it" and "Not this time".

**Results**
- [ ] Each game card shows who said yes, who brings it, and "Nobody can bring it yet" when nobody offered.
- [ ] Each date card shows, besides who can come, which proposed games would be missing that day because nobody who offered to bring them can come ("Sat 8 Nov: nobody to bring Humanity, Ruben can't come").
- [ ] "No" answers are shown to the host only.

**The pick makes a game night**
- [ ] When the host picks a date in a poll with games, a game night is made (a new concept next to the game day, `GameNightCreated` and its own join events) with the date, title, place, time zone, the host and everyone who can come.
- [ ] Its plan holds the proposed games someone can bring that day, each with who brings it (the first one who offered and can come). Games nobody can bring that day are listed apart as missing.
- [ ] Every yes from someone who can come becomes a wish on that game for the night; wishes from people who can't come are dropped.
- [ ] The game night page (members only) shows the date, place, who's coming, the plan with who brings what, and "Pick a game for my table": the same matching as a game day, with the plan as the shelf.
- [ ] The poll's existing link leads to the game night once picked, as it leads to the game day today.
- [ ] Erasure: a game night drops an erased player like a game day does (their offers, answers and place at the table) until it's over; their data is in the export (`PersonalData`).

**Not in this version:** checkpoints, play log, closing at midnight, the recap mail, editing or cancelling a game night, its own invite link.
