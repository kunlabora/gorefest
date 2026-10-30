# A2 Names: every button, link, toggle and image says what it is

Source: product idea, 2026-10-30. Status: ✅ built. Design: [Accessible by construction](a-accessible-by-construction.md).

**Idea:** buttons, links, icon buttons, toggles and images take their accessible name or alt as a required, non-blank parameter, and the ARIA attributes views write by hand become typed parameters on components.

## Criteria

- [x] A11Y-4: Every button and link has a name a screen reader can say, icon-only ones included, and one of several alike says what it acts on.
- [x] A11Y-5: Decorative icons and pictures are hidden from screen readers; an image that informs says what it shows.
- [x] A11Y-6: A toggle says whether it is on.
- [x] `button`/`submitButton`, `iconButton`, `textLink`, `toggle(pressed)`, `image(alt: Alt)`, `AccessibleName` refuses a blank string.
- [x] `ArchitectureTest`: no view calls `button(` or `img(`, or sets `aria-*` or `role`; every control on a page the web tests parse has a name (see notes).
- [x] Screens look as before; no event, route or stored data changes.

Built as designed, with these notes:

- Rule 3 ("every public component rendering a button, link or input takes a `label`") became a rendered check instead: `page()` and `fragment()` in `HtmlAssertions` assert that every button, link and form control has a name and every image an alt. It covers what the rule would have (components) and what it couldn't (a label computed to nothing), and every web test runs it without a new test.
- Rule 2 needed the three hand-written live regions gone too, so `busyNotice`, `liveLine` and `liveSection` arrived here rather than in A3; A3's `swapTarget` builds on them. The wizard's progress bar moved into `progressSteps`.
- The heart on the bring page now also names its game ("I want to play Brass"), like the poll's chips (BUG-035); before, every row's heart had the same name.
- Buttons that had no `type` (sync, "Make a new link") are now `type="button"`; none sits in a form, so nothing changes for them.
- The rules skip comments now, so a KDoc that mentions `button(` isn't a call.
- `Alt` sits in `Alt.kt` with `image` and `decoration` (detekt's `MatchingDeclarationName`).

After review (2026-10-30, an adversarial review of `8bd8950^..7f33499`):

- Alike buttons didn't say what they act on: "Approve"/"Reject" per person, the admin's request buttons, the poll's "Add" per game and "Pick this one" per date, "See games" per member. Each now has `says` ("Add Azul to the poll", "Pick Saturday 7 November"), `linkButton` takes `says` too, and `page()`/`fragment()` fail when buttons or links share a name but do different things.
- The wizard's progress bar said "0" while the screen showed "1 / 12": it now says "Pair 1 of 12".
- The rules' comment stripper treated `//` inside a string (`"https://…"`) as a comment; it now skips strings first.
