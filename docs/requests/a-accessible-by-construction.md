# A1–A4 Accessible by construction

Source: product idea, 2026-10-30. The shared design for A1–A4 ([milestone 3](../milestone-3.md)); each story's criteria are in its own file: [A1 Forms](a1-forms.md), [A2 Names](a2-names.md), [A3 Structure](a3-structure.md), [A4 Colour and motion](a4-colour-motion.md).

**Idea:** meet WCAG 2.2 AA's basics because the code can't be written any other way, not because a test run happens to catch a slip. The pages are built from our own kotlinx.html components, so the components carry the accessibility (labels, names, landmarks, live regions), and `ArchitectureTest` rules make sure no view goes around them. What a type or a rule can't see (colour contrast, ids across a page) is checked statically on the CSS and on the rendered HTML the web tests already produce. A browser-driven scanner (axe) and a manual VoiceOver and keyboard pass stay out of CI: they're for an occasional look, not a guardrail.

**Today:** the groundwork is partly there. `page()` sets `lang`, renders `<header>`, `<main>`, `<footer>` and a nav named "Main"; `segmented` and the chip groups use `<fieldset>` with a hidden `<legend>`; `fieldError` and error notices have `role="alert"`; `:focus-visible` has a 3px ring; `prefers-reduced-motion` is honoured; small buttons and chips are 44px tall; U12 and U13 fixed two contrast problems by hand. But nothing holds it in place:

- `field(id, label) { input(...) }` points the label at `id` and trusts the lambda to give the control that id. Hints and errors aren't tied to the control (`aria-describedby`), and `aria-invalid` is set by hand in three views.
- Views call raw kotlinx.html tags: about 38 `button`, 14 `input`, 34 `h1`, 31 `h2`, 1 `img`, and set `attributes["aria-…"]` or `role` 30 times. Each one is a place where a name, a label or a level can be forgotten.
- Heading levels are picked per view, so a page can have two `<h1>` or jump from `h1` to `h3`.
- Swapped-in htmx results announce themselves in three places (`aria-live`), by hand.
- Contrast was checked by eye, once.

**Vocabulary:** none new for players. In code: an **accessible name** is what a screen reader says for a control (its text, `aria-label` or `aria-labelledby`).

## Behaviour

Nothing changes in what people can do. Someone using a screen reader, the keyboard, a zoomed screen or a dark theme gets:

- every form control read out with its label, its hint and, when refused, its error, and marked invalid;
- every button and link with something to say, icon-only ones included ("Take Brass off the poll", "Your account"), and decorative icons and box art kept quiet;
- toggles (heart, bring, sort chips) that say whether they're on;
- one main heading per page, headings in order, a main region and named navigation, so the rotor's heading and landmark lists make sense;
- results swapped in by htmx (search results, suggestions, a refused form) announced, without the page jumping;
- text that stays readable on every surface in both themes, and a focus ring that's always visible.

## Design

**Events, deciders, read model, matcher:** no change. This story is all `shell.web` and the design CSS.

**Components** (`web/components/`), each the one way to render its element:

- `Forms.kt`
  - `field(id, label, hint, error) { textInput(…) / emailInput(…) / dateInput(…) / … }`: the field owns the control. Its control builders are extension functions on a `FieldScope` that only exists inside `field`, so the control can't be rendered without a label, and it gets `id`, `aria-describedby` (hint and error ids) and `aria-invalid` from the field. Typed values stay as they are (`value`, `required`, `maxLength`).
  - `switch(name, label, checked)` for the `gf-switch` checkboxes (poll form, collection opt-out), label included.
  - `hiddenInput` stays (a hidden input needs no label).
  - `dateField` (the poll's repeated date rows) becomes a `field` control with a label per row ("Date 2").
- `Buttons.kt`
  - `button(label, style, size, type, …)` and `submitButton(…)`: the visible label is a required parameter. All existing `button(type = …, classes = buttonClasses(…))` calls in views move here.
  - `iconButton(label: String, icon)`: renders `aria-label` from `label` and the icon `aria-hidden`. Labels go through `AccessibleName`, a value class that refuses a blank string.
  - `linkButton` and `textLink` (`gf-link` buttons) likewise take a required label.
  - `toggle(label, pressed: Boolean, …)` renders `aria-pressed`; views stop writing it.
- `Text.kt` / `Page.kt`
  - `page(title, heading = …)`: the page renders its own single `<h1>` (from `heading`, defaulting to `title`) with the landing or picker style as a parameter. Views stop calling `h1`.
  - `section(heading) { … }` renders `<section aria-labelledby>` with an `<h2>`; a `section` inside a `section` gets `<h3>`. The level comes from nesting, not from the view. A visually hidden heading is `section(heading, hidden = true)` (today's `h2("gf-vh")`).
  - `label`-styled headings (`h2("gf-label")` in the poll) become `section` headings with the label style.
- `Images`: `image(src, alt: Alt)` with `Alt.Text("…")` or `Alt.Decorative`. Box art stays `Decorative` (the game's name is always next to it, linked). `bggCredit` uses `Alt.Text`.
- `Feedback.kt`: `swapTarget(id, announce = Announce.POLITE) { … }` for every region htmx swaps a result into: it renders the id and `aria-live`. The three hand-written `aria-live` places use it. A refused form already answers with `role="alert"` (`fieldError`, `errorNotice`); that stays.
- `aria(…)`: the remaining attributes views need (`aria-controls` on the suggestions' time chips, the wizard's `progressbar` values) become typed parameters on the components that use them (`progress(value, max, label)`), not free strings.
- **Dialogs and popovers:** none exist today. The rule below forbids hand-rolled ones (`role="dialog"`), so the first screen that needs one adds a `dialog` or `popover` component on the native elements (Esc and focus come with them). Not built in this story.

**Rules** (`ArchitectureTest`, Konsist on source text, like the existing rules; each failure names the file and the component to use instead):

1. Outside `web.components`, no view calls `input(`, `select(`, `textarea(`, `button(`, `img(`, `h1(` … `h6(`, `fieldSet(` or `label(`.
2. Outside `web.components`, nothing sets `attributes["aria-…"]`, `attributes["role"]` or `role =`.
3. Inside `web.components`, every public function that renders a `button`, an `a` or an `input` takes a parameter named `label` (or is in a short allow-list with the reason written next to it, like `hiddenInput`).

**Rendered checks** (`HtmlAssertions.page()`, so every web test runs them on every page it parses, like the `hx-target` check today): ids are unique; every `for`, `aria-labelledby`, `aria-describedby` and `aria-controls` points at an id on the page; exactly one `<h1>`; heading levels don't skip; `<html lang>`, one `<main>` and every `<nav>` named. `fragment()` runs the id checks on the fragment.

**CSS checks** (a plain unit test in `web.components`, reading `docs/design/css/tokens.css` and `gorefest.css` from the classpath): resolves the custom properties (`var(--x)` chains) per theme (light, dark by media query, dark forced) and asserts contrast for a declared list of pairs:

- text tokens (`ink`, `ink-muted`, `placeholder`, `brand-ink`, `danger`) on every surface (`surface`, `surface-raised`, `surface-sunk`, the `-soft` fills they're used on): at least 4.5:1;
- `on-brand`/`brand`, `on-accent`/`accent`, `on-olive`/`olive`: at least 4.5:1;
- non-text: `line-strong` and `focus` on every surface: at least 3:1 (WCAG 1.4.11);
- `:focus-visible` is defined, a `prefers-reduced-motion` block exists, and `.gf-btn--sm`, `.gf-chip` and `.gf-iconbtn` are at least 24px (WCAG 2.5.8; they're 44px today).

The pairs list lives next to the test with a line saying which component uses each; a new token pair that carries text is added there.

**Migration:** every view moves to the components (about 120 call sites in 15 `XViews.kt` files). The markup each component renders matches what the view rendered by hand, so the CSS and the mockups don't change; where a hand-written view was missing a label, a name or a level, the component adds it. Existing web tests keep passing on the same selectors.

**Web:** no new routes or packages. **Privacy:** no change: nothing new is stored, shown or logged.

## Decisions

- **Decided (2026-10-30):** four stories, each landing its components, its rule and its migration together:
  - A1 Forms: `field` owns the control, `switch`, rule 1 for `input`/`select`/`textarea`/`label`/`fieldSet`.
  - A2 Names: buttons, icon buttons, links, toggles, images, rule 1 for `button`/`img`, rule 2, rule 3.
  - A3 Structure: `page` owns the `<h1>`, `section` headings, `swapTarget`, rule 1 for headings, the rendered checks in `page()`.
  - A4 Colour and motion: the CSS checks, and fixing whatever pair fails.
- **Decided (2026-10-30):** a new milestone 3, group prefix `A`.
- **Decided (2026-10-30):** a new contract area `docs/contract/a11y.md`, prefix `A11Y`. The `ArchitectureTest` rules are the proof for "every page" promises, so they carry `@Obligation` tags too.
- **Decided (2026-10-30):** a contrast pair that fails is fixed in `tokens.css` within A4 (as U12 and U13 did), checked against the mockups in both themes.

## Obligations

`docs/contract/a11y.md`, added by the story that proves them:

| Id | Obligation | Story |
|---|---|---|
| A11Y-1 | Every form control has a label tied to it, so a screen reader names it. | A1 |
| A11Y-2 | A field's hint and error are read with its control, and a refused field is marked invalid. | A1 |
| A11Y-3 | A set of choices (segmented control, chips, switches) is announced with its question. | A1 |
| A11Y-4 | Every button and link has a name a screen reader can say, icon-only ones included. | A2 |
| A11Y-5 | Decorative icons and pictures are hidden from screen readers; an image that informs says what it shows. | A2 |
| A11Y-6 | A toggle says whether it is on. | A2 |
| A11Y-7 | Every page names its language and has one main heading, one main region and named navigation. | A3 |
| A11Y-8 | Headings follow each other without skipping a level. | A3 |
| A11Y-9 | Ids on a page are unique, and every reference to one (labels, descriptions, controls, htmx targets) resolves. | A3 |
| A11Y-10 | A result htmx swaps in is announced to a screen reader; a refusal is announced at once. | A3 |
| A11Y-11 | Text has at least 4.5:1 contrast on every surface it's used on, in the light and the dark theme. | A4 |
| A11Y-12 | Borders that mark a control and the focus ring have at least 3:1 contrast on every surface, in both themes. | A4 |
| A11Y-13 | The keyboard focus is always visible, and motion calms down when the device asks for less. | A4 |
| A11Y-14 | Every button, chip and icon button is at least 24px in both directions. | A4 |

A manual VoiceOver and keyboard pass is the one check not automated; A4 lists it under Known gaps in `docs/contract/README.md`.
