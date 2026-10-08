# Brand book

A companion at the game table: playful, confident, a bit nerdy. Warm paper, charcoal ink, one loud chocolate-orange for "go", and dice. Everything is plain HTML and CSS classes (`gf-*`) on top of `tokens.css`, built for server-rendered htmx pages: no framework, no build step.

## Voice

- **Talk like the friend who knows the shelf.** Second person, short sentences, contractions. "Nobody's played it and everyone's likely to enjoy it." Never "Based on your preferences, we recommend…".
- **Sentence case everywhere**, including buttons ("Join Gorefest #7", "Let me in"). British spelling: *favourites*, *colour*.
- **Use the product vocabulary**: game day, shelf, collection, suggestions, checkpoint. Say "game day", never "event" or "session".
- **Buttons say what happens**, first person when it's about the user: "Send me a code", "Let me in", "Bring games". Avoid "Submit", "OK" and "Continue".
- **One joke per screen, at most**, and never in errors. Errors say what went wrong and what to do: "That code doesn't match. Two more tries, then we'll send a fresh one."
- **No emoji in UI text.** Personality comes from the type, the dice and the copy.
- See the *Microcopy* section for the wizard answers, payoff and suggestion reasons.

## Colour

The palette is five colours: `charcoal-brown`, `olive-bark`, `golden-bronze`, `light-yellow` and `chocolate`. Components never use them directly; they use the semantic tokens, which swap per theme.

- Page on `surface`; cards, picks and inputs on `surface-raised`; wells (art letterbox, segmented track) on `surface-sunk`.
- Text in `ink`; secondary text in `ink-muted`. Both are checked against all three surfaces and every `*-soft` tint in both themes.
- **`brand` (chocolate) means "go"**: the primary button, the picked card, filled progress. At most one `brand` fill per screen besides progress. Text on it is always `on-brand` (near-black); white on chocolate is only 3.2:1.
- `accent` (golden-bronze) is for marks only: stars, the "Both great" icon, the checked-chip tick. Never text on `surface`.
- `olive` means *played / enjoyed*: reason icons, the bringing switch, "Played both" tags.
- `danger` is an added red (the palette has none) so chocolate never has to mean "wrong". Every error pairs it with the alert icon and words.
- Hairlines use `line`; anything you can operate has a `line-strong` border (≥ 3:1).
- Focus is always `focus` (= `ink`): 3px solid, 2px offset.

## Type

- **Bricolage Grotesque** (`--font-display`, 600–800) for headings, game names and numerals. It's the loud one: use `hero` once per screen at most, `h1` for the screen question, `game-name` on cards.
- **Atkinson Hyperlegible Next** (`--font-sans`, 400/700) for everything else. It was designed for low-vision legibility, which helps in dim rooms and small in-app browsers.
- Both load from Google Fonts in `bundle.css`. For production, self-host the two woff2 files next to the jar and swap the `@import` for `@font-face`.
- Inputs are ≥ 16px so iOS doesn't zoom. Game names wrap with `overflow-wrap: anywhere` and clamp (3 lines on picks, 2 on cards).

## Shape, space and depth

- 4px base: `space-1` to `space-7` (4 → 48). The screen gutter is `space-4`; sections are `space-5` apart.
- Corners: `radius-lg` for cards and picks (the "box lid"), `radius-md` for buttons, inputs and art, `radius-pill` for chips, avatars and switches.
- **Cardboard, not glass.** Resting things get a hard bottom edge (`shadow-edge`, `shadow-brand-edge`), and pressing removes it and moves the element down 3px. No soft floating shadows except `shadow-lift` on fanned box art.

## Box art

BoardGameGeek thumbnails come in any ratio and quality. Always put them in `.gf-art`:

- The frame is square (or `--wide` 2:1). The image is `object-fit: contain`. Set `style="--art:url(…)"` on the frame so a blurred copy fills the letterbox, which makes portrait and landscape boxes look intentional.
- No image: render `.gf-art--missing` with a two-letter monogram (`data-hue` 0–2, chosen from the BGG id) instead of a broken image.
- `alt=""` when the game name is next to the art (it almost always is).

## Motion

- Presses: 80–90ms. The wizard "deals" the next pair: add `.gf-deal` to the swapped-in `.gf-duel` (slide up 14px, 200ms, card B 50ms later).
- Busy: htmx's `.htmx-request` class (or `aria-busy="true"`) shows `.gf-spin` inside buttons and picks. There's no separate loading screen, because every swap is one fragment.
- `prefers-reduced-motion` turns off dealing and transitions.

## Layout

- Mobile-first, one column. Primary actions live in `.gf-dock`, a sticky bar at the bottom, in reach of the thumb. On the wizard, the pair and the answers sit at the bottom and the question sits at the top.
- Every page sits in the wide frame (`.gf-screen--wide`): a 1080px column (`wrap`), with full-width `band`s (the sunk surface groups secondary material). From 720px, `split` puts copy and picture side by side; below that everything stacks. Desktop and phone are one markup.
- The app bar is the brand: the wordmark everywhere, plus a nav (Game days, Collection) and your avatar once signed in (`SiteBar`). Back links are a `crumb` above the heading, not an arrow in the bar.
- The sticky dock is for phones only, where the action is in thumb reach; on desktop the action sits beside its field or content (`.gf-phone-only` / `.gf-desk-only`).
- **Build a new screen from the shared pieces, not from its own CSS:** `wrap` / `band` / `split` for layout, `gf-card` (+ `gf-tilted`) for raised cards, `stepper` / `step` for anything numbered, the `gf-signin` row for a field with its button. A screen block in `gorefest.css` holds only what is truly its own. If a second screen wants the same thing, it moves into the shared block first. See `mockups/component-layout.html`.
- The pattern for every screen is the login's: kicker, big headline, one line of help, the action, and the product shown on tilted raised cards. See `Gorefest App Redesign.html`.
- Wizard picks accept keyboard shortcuts: the `A`/`B` key hints on each pick are real. Wire them with a small hyperscript handler.

## htmx patterns

- **Wizard**: one `<form hx-post hx-target="#wizard" hx-swap="outerHTML">`; every answer is a `<button name="answer" value="A|B|UNPLAYED|BOTH_MEH|BOTH_GREAT">`. The response is the next `#wizard` (or the payoff page via `HX-Redirect`).
- **Suggestions**: the table form uses `hx-trigger="change"` and swaps `#suggestions`. Chips and the segmented control are real checkboxes and radios.
- **Game day page**: one `.gf-next` card holds the single next action (taste round before the day, checkpoint on the day).
- **Toggles** (bringing): `<input class="gf-switch" type="checkbox" role="switch" hx-post … hx-trigger="change">`.

## Iconography

Our own 24px line set: 2px stroke, round caps and joins, drawn on a 24 grid (see the Icons group). Inline the SVG with `stroke="currentColor"` and class `gf-icon` so it takes the text colour; the uploaded files are single-ink `charcoal-brown`. Icons always sit next to a word, never alone, except in `.gf-iconbtn`, which then needs an `aria-label`.

## Accessibility

- Real `<button>`, `<input>`, `<label>` everywhere; chips, tiles and segments are visually restyled native checkboxes and radios.
- Selection is never colour-only: checked chips swap the avatar for a tick, segments get an ink border, tiles get a tick badge.
- Every text pair meets 4.5:1 in both themes, and control borders and focus rings meet 3:1. The one exception is in the palette itself: `golden-bronze` is 3.0:1 on `light-yellow`, so it is never used for text there.
- Hit targets are ≥ 44px; wizard picks are about half the screen each.

## Screens

The *Screens* group has phone mockups (360px wide) of every screen in the brief: wizard, payoff, suggestions, checkpoint log, invite landing, login, the Open Graph image, the game day hub (with bringing games), host readiness, create game day, BGG linking and home. Switch theme to see them in dark. Box art in the mockups is abstract placeholder art.
