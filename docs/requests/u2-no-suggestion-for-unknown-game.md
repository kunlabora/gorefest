# U2 Don't suggest games a player said they don't know

Source: user testing, 2026-10-06. Status: 📋

**Feedback:** A player got a suggestion for a game they had already marked as one they don't know.

**Wanted:** investigate how that happens (the wizard's "haven't played" answer is probably treated as no information for the matcher, so the game still scores on features). A game the player has marked as unknown/unplayed must be treated as such: it is allowed as a *novelty* suggestion at most, never presented as something they know and like, and the reason line must say so.

**Criteria**
- [ ] Reproduce with a test (given a "haven't played" answer, when suggesting, then ...)
- [ ] Decide and document the rule in `core` (what the matcher does with games a player declared unknown)
- [ ] The suggestion reason reflects it
