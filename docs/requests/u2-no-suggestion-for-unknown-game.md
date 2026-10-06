# U2 Don't suggest games a player said they don't know

Source: user testing, 2026-10-06. Status: ✅

**Feedback:** A player got a suggestion for a game they had already marked as one they don't know.

**Wanted:** investigate how that happens (the wizard's "haven't played" answer is probably treated as no information for the matcher, so the game still scores on features). A game the player has marked as unknown/unplayed must be treated as such: it is allowed as a *novelty* suggestion at most, never presented as something they know and like, and the reason line must say so.

**Criteria**
- [x] Reproduce with a test (given a "haven't played" answer, when suggesting, then ...)
- [x] Decide and document the rule in `core` (what the matcher does with games a player declared unknown)
- [x] The suggestion reason reflects it

**Rule (in `Matcher`):** a game a player declared unplayed ("Haven't played") and never compared (`PlayerGames.isUnknownToThem`) is neutral for that player: their predicted enjoyment counts as 0 (an average game) instead of a guess from its features. It can still be suggested on novelty, and the reason is `SuggestionReason.NewToSome(names)`: "New to Kim: a chance to try it." It never claims they'd enjoy it. If they later compare it ("which would you rather try?") the answer is real information and the game scores normally. Test: `MatcherTest` (a declared-unplayed game gets only the novelty bonus, with the new reason).
