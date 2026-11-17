# U23 Don't log a play for a table of one by mistake

Source: Tim logged a game and then saw he was the only one at the table, 2026-11-17. Status: 📋 todo

**Idea:** a play log should name the people who really played, and a wrong one can be put right.

**Today:** the log form (`/gamedays/{id}/log`, `CheckpointViews.logForm`) starts with only you ticked under "Who
played?" (PL-6). Nothing questions a table of one, so a quick "Log it" records `PlayLogged` with one player. That play
marks the game played for you only (PL-10), teaches the group speed from it (PL-11), shows on the hub (PL-9), and can't
be undone: there is no event that takes a play back. The suggestions page doesn't link to the log, so the table you just
picked suggestions for is lost. Solo plays are real (many games play with one), so a table of one can't simply be
refused.

## Behaviour

1. **Start from the right table.**
   - Each suggestion gets a "We played this" link to the log with that game and that table already filled in.
   - From the hub, the form ticks the players of your last logged play on this game day, if you were in it; otherwise
     just you, as today.
2. **Refuse what can't be true, question what's odd.**
   - Fewer players ticked than the game's minimum (BGG `minPlayers`) is refused, and the form stays as filled in:
     "Brass: Birmingham needs at least 2 players. Tick who else played."
   - Just you ticked, on a game day with others, for a game that plays with one: the summary card and the phone dock say
     "Just you? Tick who else played." The log still goes through as is.
3. **Put it right afterwards.**
   - In "Played today" on the hub, a play you logged or played in has **Remove**. Removing takes the play away
     everywhere: the hub list, played status, novelty, the group speed. The form opens again with that play filled in, so
     a wrong table is fixed by removing and logging again.
   - Removing is possible whenever logging is (from the game day's date, also after it ended), not on a cancelled game
     day. The "which did you enjoy more?" answer given after the play stays: it is the player's own opinion.

## Design

**Events** (new type only):

- `PlayRemoved(gameDay, game, loggedBy, loggedAt, removedBy, at)`: names the play by the fields `PlayLogged` already has
  (game day, game, who logged it, when), because `PlayLogged` has no id and the plays already logged (Tim's included)
  must be removable. Tags `gameday`, `game`, and `player` for every player of the removed play (the shell copies them
  in), so the same per-player projections see it.

**Deciders** (`core/gameday/Plays.kt`):

- `LogPlayDecider` takes a command `LogPlay(play: PlayLogged, minPlayers: Int)` instead of the event itself, so the
  game's minimum is in the command (the core has no BGG metadata). New rejection `TooFewPlayers(minPlayers)`. Query
  unchanged.
- `RemovePlayDecider(RemovePlay(...))`. Query: the game day's created/cancelled/details and `PlayLogged`/`PlayRemoved`
  tagged with the game day and game. Rejections: `NoSuchGameDay`, `GameDayCancelled`, new `NoSuchPlay` (also when
  already removed: a double tap is a no-op instead), new `NotYourPlay` (only the logger or a player in it).

**Read models:**

- `GameDayPlays` drops a removed play from the list and from group speed.
- `PlayerTastes`: played status today is one Boolean per game, last word wins. A removal must give back what was true
  before the play, so the projection keeps the logged plays per player and game apart from declarations;
  `hasPlayed` is true if a declaration says so or any play that isn't removed names them.

**Web** (`web.checkpoint`, `web.suggestions`, `web.gameday`):

- `GET /gamedays/{id}/log?game=…&players=…` fills the form (unknown players and games are ignored, as PL-14 does).
- `POST /gamedays/{id}/plays/remove` (htmx: replace the "Played today" section, `announce("Removed …")`), then a link
  "Log it again" to the filled-in form.
- The "Just you?" line is in the summary's hyperscript and in the server-rendered summary.

**Privacy:** nothing new is shown; a removal names who removed it by id only. `PersonalData.describe` gets a case.
`PlayerErased`: unchanged; removals of plays naming an erased player still work.

**Matcher:** no change; it reads played status and speed, which follow the removal.

## Open questions

1. **Who may remove a play?** Recommendation: whoever logged it and anyone named in it. Not the host for others' plays.
2. **Edit instead of remove-and-log-again?** Recommendation: remove and log again; one new event, and the form already
   exists.
3. **Hub default after the first play**: tick the table of your last play? Recommendation: yes, people tend to stay at
   their table.

## Criteria

The obligation ids are reserved here and go into `docs/contract/plays.md` when the build starts.

- [ ] PL-19 (replaces ~~PL-6~~'s "has me ticked"): coming from a suggestion, the log form has that game and that table
  filled in; from the hub, the table of your last play on this game day, else just you.
- [ ] PL-20: a play with fewer players than the game's minimum is refused with the minimum named, and the form keeps
  what was filled in.
- [ ] PL-21: logging just yourself on a game day with others shows "Just you?" in the summary and the dock, and still
  logs.
- [ ] PL-22: the logger or a player in a play can remove it, also a play logged before this story; anyone else can't;
  removing twice changes nothing; a cancelled game day can't.
- [ ] PL-23: a removed play is gone from the hub, the group speed and played status (a game played only in that play is
  unplayed again, its novelty back), and the comparison answered after it stays.
- [ ] Event evolution: new type only, sample in `EventCompatibilityTest`, `PersonalData.describe`, `CLAUDE.md` events
  list.
- [ ] Members-only and cancelled-day web tests for the log link and remove.
