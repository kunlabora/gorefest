# A1 Forms: every control has its label, hint and error

Source: product idea, 2026-10-30. Status: ✅ built. Design: [Accessible by construction](a-accessible-by-construction.md).

**Idea:** a form control can only be rendered inside `field`, which gives it its id, its label, and ties its hint and error to it. A rule keeps views from rendering a control by hand.

## Criteria

- [x] A11Y-1: Every form control has a label tied to it, so a screen reader names it.
- [x] A11Y-2: A field's hint and error are read with its control, and a refused field is marked invalid.
- [x] A11Y-3: A set of choices (segmented control, chips, switches) is announced with its question.
- [x] `field` owns its control (`FieldScope` builders); `switch` for the `gf-switch` checkboxes; the poll's date rows each have a label.
- [x] `ArchitectureTest`: no view outside `web.components` calls `input(`, `select(`, `textarea(`, `label(` or `fieldSet(`.
- [x] `docs/contract/a11y.md` exists and `docs/contract/README.md` lists it.
- [x] Every form looks as before (browser, desktop and 375px, light and dark); existing web tests pass on the same selectors.
- [x] No event, route or stored data changes.

Built as designed, with these notes:

- The poll's date rows keep one `aria-label` ("Date") each; the list is a `labelledGroup` named by "Which dates could work?", which says more than numbering them.
- `FieldControl` sits in its own file (detekt's `MatchingDeclarationName`).
- The Contract's parser only read prefixes of capital letters, so `A11Y` was skipped without a word: prefixes may now hold digits, an area the parser reads no obligations from fails the build, and the test task tracks `docs/contract` so a Contract change isn't served from the cache.
- The bring tile and the collection switch were checked by their web tests, not in the browser (the dev account had no linked collection); the markup is the same as before.

After review (2026-10-30, an adversarial review of `4312cdd`):

- Game tiles (the log's and the watched form's "which game?", the poll form's games, and their search results) were bare radios and checkboxes with no group, so a screen reader never heard the question. They now come from `gameTiles(question, name)`, and `gameTileChoice` needs that group in context.
- The rule had holes: it now also catches `textArea`, kotlinx's typed input builders (`checkBoxInput`, `radioInput`, …), calls without parentheses and calls on a receiver. Our `textInput` became `fieldInput`, because kotlinx.html has its own unlabelled `textInput` and the rule couldn't tell them apart.
- A11Y-1 to A11Y-3 are now also checked on every page and fragment the web tests parse (`HtmlAssertions`): unique ids, labels and descriptions that point at ids that exist, and choices sharing a name inside a named group (switches excepted: each carries its own name). `PageChecksTest` shows the checks catch what they claim.
- `ContractCoverageTest` also fails on a line that looks like an obligation (`- **`) but doesn't parse, not only on an area with none.
