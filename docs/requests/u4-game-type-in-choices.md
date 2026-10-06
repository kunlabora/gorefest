# U4 Show a game's type/category to help make choices

Source: user testing, 2026-10-06. Status: 📋

**Feedback:** The "type" or category of a game helps people choose.

**Wanted:** show one or two category/mechanic words (e.g. "Card game", "Cooperative", "Deck building") on the wizard duel cards and on suggestion cards, taken from the BGG metadata we already cache.

**Criteria**
- [ ] A short, display-friendly "type" derived from BGG categories/mechanics (`Game.facts` neighbour in `shell.web`)
- [ ] Shown on duel cards and suggestion cards, with a web test
