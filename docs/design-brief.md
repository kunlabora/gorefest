# Design brief: boardgame game-day matcher

*Working name "Gorefest", after our group's game days. The product name is still open.*

## The product in one paragraph

A friend group holds a **game day**: one host, 4–12 players, a pile of board games on a **shelf**. The app learns each player's taste from quick "which of these two?" questions, then suggests the **top 3 games** for whoever is sitting at a table, each with a one-line reason. It ends the "what shall we play?" debate that eats the first 30 minutes of every game day.

## Where and how it's used

- **Phones only, in practice.** People open the invite from WhatsApp or Signal, often inside the app's built-in browser. They often use it one-handed, with a drink or dice in the other hand.
- **Short visits at checkpoints**: morning (before playing), lunch, evening. Each visit is 30 seconds to 2 minutes. There's no live updating; people refresh.
- **The mood:** friends, a table full of games, a slightly competitive nerdy crowd. It should feel playful and confident, not corporate. It's a companion at the table, not a productivity tool.
- **Game box art is the main visual material.** Thumbnails come from BoardGameGeek and vary a lot in aspect ratio and quality.

## Vocabulary (please use these words in the UI)

| Term | Meaning |
|---|---|
| Game day | The event. Has a free-text title ("Gorefest #7"), date, place, host |
| Collection | Games a player owns (imported from BoardGameGeek) |
| Shelf | Games brought to this game day |
| Suggestions | Top 3 games for a table, each with a reason |
| Checkpoint | Morning / lunch / evening moment of opening the app |

## Screens, in priority order

For each: purpose → key content → primary action. Please design empty, loading and error states where noted.

1. **Wizard step** (the heart of the product, seen most often)
   - Two games side by side or stacked: box art, name, 1–2 facts (e.g. "60–90 min · heavy · 2–4 players")
   - Five answers: **A**, **B**, **haven't played**, **both meh**, **both great**. A and B must be the biggest targets; the other three are secondary.
   - The question changes by context: "Which would you rather try?" (unplayed) or "Which did you enjoy more?" (played)
   - Progress for a 10–15 pair round, and a way to stop early
   - Each tap moves straight to the next pair; it should feel like flicking through cards, not filling in a form

2. **Payoff screen** (end of first wizard round)
   - "Your likely favourites today: X, Y, Z" from this game day's shelf
   - Should feel rewarding: this is where people decide the app is worth it
   - Open question: also show taste overlap with others ("you and Anna both love heavy euros")?

3. **Suggestions** (on the day)
   - Pick who's at the table (avatar or name chips, multi-select) plus an optional time budget
   - Three suggestions, each with box art, name, playtime estimate for this group, and a one-line reason ("Nobody's played it and everyone's likely to enjoy it")
   - State: "nothing fits", with a hint for which constraint to relax

4. **Checkpoint log**
   - "What did you play?" → game, who played, roughly how long → one comparison question → fresh suggestions
   - Must be done in under 30 seconds

5. **Invite landing page** (first impression for every participant)
   - Host, title, date, place, shelf preview (some box art), "Join"
   - Also the **Open Graph preview image** for WhatsApp/Signal: title, date, place, shelf size

6. **Login**
   - Enter email → "Check your mail". The mail has a magic link **and** a 6-digit code.
   - The code entry screen matters: in-app browsers often break the link, so the code is the main path for many people

7. **Game day page** (the hub)
   - Title, date, place, players who joined, the shelf, what to do next (do the wizard / see suggestions / log a game)
   - Bringing games: my collection with a "bringing" toggle per game, plus search for games not in my collection
   - Empty state: shelf with no games yet

8. **Host readiness view**
   - Who joined, whose taste profile is thin (with a nudge action), shelf size, gaps ("nothing plays well at 6")

9. **Create game day** form, **link BGG account** (enter username → put a code in your BGG profile → verify), **home page** (my upcoming game days)

## Technical constraints for the design

- **Server-rendered HTML with htmx.** Interactions replace one element with new server-rendered HTML; there's no client-side app framework. Design components as HTML and CSS; small behaviours (toggle, busy state, animate in) are fine, rich client-side state isn't.
- **Plain CSS with design tokens as custom properties** (colour, type scale, spacing, radius), light and dark. No build step: no Tailwind, no Sass.
- **Mobile-first**, with a thumb-reachable primary action; it should also look fine on a laptop.
- **Accessible:** real buttons and inputs, visible focus, sufficient contrast, and nothing that works only on hover.
- **Robust to messy box art:** different aspect ratios, missing images, long game names ("Twilight Imperium: Fourth Edition").

## What would help most back

1. A small **design system**: tokens, type, buttons (primary / secondary / answer-choice), chips, game card (compact and large), form fields, list rows, empty states.
2. **Mockups** of screens 1–5 at phone width, in light and dark.
3. A proposed **name and tone of voice**, if inspiration strikes: sample microcopy for the wizard answers, the payoff screen and the suggestion reasons.
