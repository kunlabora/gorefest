# U4 Show a game's type/category to help make choices

Source: user testing, 2026-10-06. Status: ✅

**Feedback:** The "type" or category of a game helps people choose.

**Wanted:** show one or two category/mechanic words (e.g. "Card game", "Cooperative", "Deck building") on the wizard duel cards and on suggestion cards, taken from the BGG metadata we already cache.

**Criteria**
- [x] A short, display-friendly "type" derived from BGG categories/mechanics (`Game.facts` neighbour in `shell.web`)
- [x] Shown on duel cards and suggestion cards, with a web test

**Done:** `Game.typeWords` / `Game.typeText` (in `shell.web.components.Games.kt`, beside `facts`) pick up to two words from BGG categories and mechanics by a fixed priority list ("Cooperative", "Card game", "Deck building", ...). Shown as `.gf-pick__type` on the wizard's duel cards and `.gf-game__type` on ranked games (suggestions, and the payoff podium). Tests: `ComponentsTest`, `TasteRoutesTest`, `SuggestionsRoutesTest`.
