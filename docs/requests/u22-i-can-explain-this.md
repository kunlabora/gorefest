# U22 I can explain this game

Source: product idea, 2026-11-17. Status: 🚧 doing

**Idea:** a player marks the games in their collection that they can explain. When a table picks a game, a game that
someone at the table can teach is an easier start than one nobody knows, so the suggestions lean towards it and say who
can explain it.

**Today:** nothing records who knows the rules. The matcher favours novelty (`noveltyBonus` when nobody at the table
played it, `SuggestionReason.NobodyPlayedIt` / `NewAndLiked`), which is exactly the case where nobody can teach it and
the table spends half an hour reading the rulebook. The only per-game declarations a player makes outside a game day are
"Haven't played" (`GamePlayStatusDeclared`) and comparisons; on a game day there are hearts (`PlayWishChanged`).

**Vocabulary:** an **explainer** of a game is a player who said they can explain (teach) it. It's a lasting fact about
the player, not about one game day.

## Behaviour

- A player can mark games in their collection, and any game on the shelf of a game day they joined (someone else's
  copy). On the collection page every game has a toggle **I can explain this** (off by default). Tapping it saves at once, like
  a heart on the bring page. It can be taken back.
- On the bring page, the player's own games and every game on the shelf carry the same toggle, so a host ticking games
  can say it in the same pass, and anyone can say "I can explain Bram's Brass".
- On the shelf (bring page, "On the shelf" list) a game with an explainer among the game day's players says so:
  "Anna can explain it", or "Anna and Bram can explain it", or "3 people can explain it".
- Suggestions: when someone at the table can explain a game and someone at the table hasn't played it, the game gets a
  small nudge up, and the pick carries an extra line "Anna can explain it" under its reason. A game nobody at the table
  has played and nobody can explain is pushed down: someone would have to learn it from the rulebook.
- Marking a game also marks it played for that player (you can't explain a game you never played), which ends its
  novelty for them, as a play log does. It says nothing about enjoying it. Unmarking leaves it played.
- The mark stays when the game leaves the collection (a BGG re-sync), but it only shows for games on a shelf and only for
  players on that game day, so a stale mark is harmless. Unmarking stays possible from the bring page if the game is on a
  shelf.

## Design

**Events** (`core/Event.kt`, new type only):

- `CanExplainChanged(player, game, canExplain: Boolean)`, tags `player:{id}`, `game:{bggId}`. Ids only.

**Deciders** (`core/player/Explainers.kt`):

- `SetCanExplainDecider(SetCanExplain(player, game, canExplain))`. Query: `CanExplainChanged` tagged
  `player:{id}` + `game:{bggId}` (nothing else can conflict). Setting the value it already has is a no-op (no event).
  No rejections: the shell checks the game is in the player's collection or on the shelf of a game day they joined
  before building the command, as U20 does for ownership; the log doesn't know collections.

**Read model** (`core/player/Explainers.kt`, exposed as `App.explainers`):

- `Explainers.canExplain(player, game)`, `Explainers.of(game, among: Collection<PlayerId>)` (the explainers among those
  players, for the shelf line and the matcher), `Explainers.gamesOf(player)` (for the collection page and the export).
- `PlayerTastes` (`core/taste/Tastes.kt`) evolves `CanExplainChanged(canExplain = true)` as played for that player and
  game (like `PlayLogged`), so `hasPlayed` is true and a "Haven't played" declaration stops making it a stranger. No
  second event: the played status follows from the mark in the projection. A later "Haven't played" still wins (last
  word counts), as it does after a play log.
- `PlayerErased` removes the player's marks entirely: they're a property of the person, not of a game day, so there is no
  closed-day history to keep.

**Matcher** (`core/matching/Matcher.kt`):

- `TablePlayer` gains `canExplain: Set<BggId>` (default empty), so the matcher stays pure and gets it from the shell like
  `PlayerGames`.
- `explainerBonus` (0.2, below the wish and novelty bonuses): added when at least one player at the table can explain
  the game **and** at least one player at the table hasn't played it (`!hasPlayed`). If everyone has played it, nobody
  needs teaching and there's no bonus.
- `unteachablePenalty` (0.2): subtracted when nobody at the table has played the game and nobody can explain it. With
  the novelty bonus (0.3) such a game still nets +0.1, so a new game nobody knows can still win on taste; it just loses
  ground to one somebody can teach (+0.5 net). The reason wording is unchanged ("Nobody has played it").
- `Suggestion` gains `explainers: List<String>` (names, as `WantedBy` does; default empty), filled whenever someone at
  the table can explain the game and someone at the table hasn't played it. It's a second line, not a new
  `SuggestionReason`, so it doesn't push out the reason that matters more (hearts, safe bet, best at this size).

**Web**:

- `web.bgg` (collection): a `chipToggle` per game row, `POST /collection/{bggId}/explain` (htmx: replace the row,
  `announce("You can explain …")` / `"… no longer marked"`), id on the toggle so focus stays.
- `web.shelf` (bring page): the same toggle on the player's own rows, `POST /gamedays/{id}/bring/{bggId}/explain`, and
  the explainer line on the shelf rows (`wishItem`). Members-only via `memberGameDay`; on an ended or cancelled game day
  the bring page is read-only, but the collection toggle still works (it isn't about the day).
- `web.suggestions`: the extra line under each pick's reason (`rankedGame` gets an optional `note`).
- Sentences in `shell.web` and `docs/design/microcopy.md`. Mockup update for collection, bring page and suggestions.

**Privacy:** new: other players on a game day see which shelf games you said you can explain. Only for games on that
day's shelf, only to its members; your full list is on your own collection page only. `PlayerErased` drops all your
marks. `PersonalData.describe` gets a case ("You said you can explain *Brass*"). `docs/gdpr.md` and the processing
register get a line (privacy-check agent).

## Open questions

All answered 2026-11-17.

1. **Decided (2026-11-17): own games plus any game on a joined game day's shelf.** **Own games only, or any game?** You often can explain a game you don't own (a friend's copy, a club game).
   Recommendation: collection plus *any* game on a shelf of a game day you joined, so "I can explain Bram's Brass" works
   too. The request said "that you own"; this widens it slightly.
2. **Decided (2026-11-17): per player, lasting.** **Per player, or per game day?** Recommendation: per player, lasting. "I can explain Brass" doesn't change between
   game days, and it saves re-marking.
3. **Decided (2026-11-17): nudge, line, and push down unteachable games.** **How strong is the nudge?** Recommendation:
   0.2 (wish and novelty are 0.3), only when someone at the table is new to the game. Tune both after a game day, like
   the U20 caps.
4. **Show the explainer when nobody's new to the game?** Recommendation: no; the line only appears when it helps.
5. **Decided (2026-11-17): yes, marking it marks it played.** **Should marking it also mean "played"?** You can't explain a game you never played, so it could set the played
   status (and end its novelty for you). Recommendation: no, keep intents separate (as U20 kept requests and hearts
   apart); a player who can explain it will usually have compared it anyway.

## Criteria

The obligation ids (SH-35–37, SU-24–26, TA-33) are reserved here and go into `docs/contract/` when the build starts,
so `check` stays green until a test proves them.


- [ ] SH-35: A player can mark a game in their collection, or on the shelf of a game day they joined, as one they can explain, and take it back; marking it twice changes nothing (decider given/when/then, web test).
- [ ] SH-36: On a game day's shelf, a game shows which of the game day's players can explain it; nobody outside the game day sees it.
- [ ] SU-24: When someone at the table can explain a game and someone at the table hasn't played it, the game is nudged up the suggestions and the pick says who can explain it.
- [ ] SU-25: A game everyone at the table has played gets no nudge and no explainer line.
- [ ] SU-26: A game nobody at the table has played and nobody can explain ranks below an otherwise equal game someone can explain.
- [ ] SH-37: When a player is erased, their marks are gone everywhere.
- [ ] TA-33: Marking a game as one you can explain marks it played for you (its novelty ends for you); unmarking leaves it played; it adds no comparison.
- [ ] Event evolution: new type only, sample in `EventCompatibilityTest`, `PersonalData.describe` case, `CLAUDE.md` events list and vocabulary.
- [ ] Ended and cancelled game days: the bring page is read-only, the collection toggle still works (web tests).
- [ ] Design mockup in `docs/design/` and the screens use existing components (`chipToggle`, `rankedGame`).
