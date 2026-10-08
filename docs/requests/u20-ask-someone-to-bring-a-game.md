# U20 Ask someone to bring a game from their collection

Source: product idea, 2026-10-08. Status: 📋 (design, not built)

**Idea:** a player who joined a game day can ask another player on that game day to bring a particular game from their collection ("Anna, could you bring Brass?"). The owner answers with one tap: bring it, or not this time.

**Today:** the shelf only grows from the inside: you tick your own games (`GameBrought`). There is no way to say "I'd love to play your Brass", other than the group chat. Wishes (U10) only apply to games already on the shelf.

**Vocabulary:** a **bring request** is one player asking another to bring one game, for one game day. A request is *open* until the owner brings the game (it is *answered*), says "not this time" (*declined*), or the asker takes it back (*withdrawn*).

## Behaviour

- Any joined player can ask any other joined player to bring a game they have in their collection. Not themselves, not guests (there are none), not before joining.
- Asking: from a member's collection ("Ask Anna to bring…", filter by game name) or from a game's own row ("Who has this?" lists members who own it and aren't bringing it yet). A game already on the shelf, brought by that owner, can't be asked.
- The owner sees the open requests on the bring page, above their collection: "Bram asks if you can bring *Brass: Birmingham*" with **Bring it** (same effect as ticking the game: `GameBrought`) and **Not this time**. Several askers for one game show as one row ("Bram and Caro ask…").
- The asker sees their open requests with a **Take it back** link, and the outcome: "Anna is bringing it" once the game is on the shelf. A declined request just reads "Not this time" and stays quiet (no blame, no reason). Open question below.
- The hub's next-step card tells the owner when something is waiting ("Bram asked you to bring Brass"), and the home hero carries a small tag. The readiness view (host) shows the number of open requests. No email or push: U19 is recorded only. Revisit when U19 lands (the events below already carry everything an update mail needs).
- Limits, so a request stays meaningful: an asker can make at most `MAX_BRING_REQUESTS` (3) requests per game day, and one owner can be asked at most `MAX_ASKS_PER_OWNER` (5) times per game day. Both count every request that stands, whether it is still open, answered or declined; a request that is **taken back stops counting** for both, so the asker gets the slot back and the owner's count drops too. Asking the same person for the same game while it's open is a no-op and doesn't count twice.
- Over once the game day ended or is cancelled (read-only, like the shelf). Leaving the shelf doesn't matter: a request is about the owner's willingness, not the shelf.
- Fulfilment is implicit: when the owner ticks the game by any route (the request row, the collection list, the search), every open request for that game to them is answered. They never have to "reply".

## Design

**Events** (`core/Event.kt`, new types only, so old logs replay): 
- `BringRequested(gameDay, asker, owner, game)`, tags `gameday`, `player:{asker}`, `player:{owner}`, `game`
- `BringRequestWithdrawn(gameDay, asker, owner, game)`, same tags
- `BringRequestDeclined(gameDay, owner, game, asker)`: the owner turns down *one* asker's request ("Not this time" on a row with two askers declines the row: one event per asker)
- Answered needs no event: `GameBrought` by the owner does it, in the projection.

**Deciders** (`core/gameday/BringRequests.kt`, pure, given/when/then tests):
- `RequestBringDecider(RequestBring(gameDay, asker, owner, game))`. Query: game day created/ended/cancelled; `PlayerJoined` for asker and for owner; `BringRequested/Withdrawn/Declined` tagged gameday+asker (the asker's cap = `BringRequested` for the asker minus `BringRequestWithdrawn`; declines and answers still count); and the same types tagged gameday+owner (the owner's cap, counted the same way); `GameBrought/Unbrought` for gameday+owner+game (already bringing). Rejections: `NoSuchGameDay`, `GameDayCancelled`, `GameDayOver`, `NotJoined` (asker), new `OwnerNotJoined`, new `CantAskYourself`, new `AlreadyBringing`, new `TooManyBringRequests` (asker's cap), new `OwnerAskedEnough`, new `OwnerNotAcceptingRequests`.
- `WithdrawBringRequestDecider`: only the asker, only while open. Withdrawing a request that isn't open is a no-op (idempotent), so a double tap is fine.
- `DeclineBringRequestDecider`: only the owner, only while open; no-op if not open.
- The existing `BringGameDecider` stays as is: its query doesn't need requests.
- **Ownership is not in the event log** (collections are plain tables synced from BGG), and neither is it checked by `BringGameDecider` today. So the *shell* checks that `owner` has `game` in their collection before building `RequestBring`, as bring-by-search does for its games. The decider can't, and shouldn't pretend to.

**Read model** (`core/gameday/BringRequests.kt`, exposed as `App.bringRequests`, no raw map outside): `GameDayBringRequests.openFor(owner, gameDay)`, `.openBy(asker, gameDay)`, `.count(gameDay)`, `.isOpen(...)`. Evolve: `BringRequested` adds, `Withdrawn`/`Declined` removes, `GameBrought` by the owner removes every open request for that (owner, game), `GameDayEnded/Cancelled` leaves them in place but the UI ignores them for closed days.

**Web** (new package `web.request`, mirrors `web.shelf`): routes `GET /gamedays/{id}/ask` (pick a member, then their collection), `POST /gamedays/{id}/ask` (htmx: replace the row with its "Asked" state), `POST …/ask/withdraw`, `POST …/ask/decline`. The owner's section lives on the existing bring page as a fragment (`#bring-requests`) so "Bring it" can reuse the bring toggle fragment. Rejections get sentences in `Rejection.message()`; the brand-book voice applies ("Not this time", never "Rejected"). Views reach for components (`playerChips`, `rankedGame`/game rows, `nextStep`, `notice`) before new `gf-*` markup; the ask screen follows the design system's list-with-action-per-row pattern. A mockup goes into `docs/design/` first, as for every screen.

**Events carry the game id only.** Like `GameBrought`, the new events hold a `BggId` and nothing displayable (core types carry no display text). The name and cover come from the BGG metadata cache (`games` table), which is never deleted from: a re-sync of the collection can't make a request point at a game we can't show. The request also doesn't depend on the collection staying the same: ownership is checked once when asking, and "Bring it" doesn't re-check it (neither does ticking a game today). If the owner drops the game from BGG afterwards, the request stays valid and can still be answered.

**Privacy and opt-out:** today a collection is visible to its owner only. This feature makes members' collections visible *to people on the same game day*, on the ask screen only. (The collection sync already imports only games with `own=1` and no expansions, so everything in a collection is owned.) The collection page gets a switch, on by default: "Let people on my game days ask me to bring games". When it is off, you don't show up on the ask screen, your games don't appear under "Who has this?", and asking you is rejected (`OwnerNotAcceptingRequests`). It is a per-player setting, so it's a new event `BringRequestsAllowedChanged(player, allowed)` (tag `player`), default allowed; `RequestBringDecider` includes it in its query. Turning it off leaves open requests as they are: the owner can still answer them. A line under the switch explains who sees what.

**Matcher:** no change for now. A request is not a wish (U10). Possible later: someone asked for it ⇒ small nudge once it's on the shelf.

## Open questions

1. (Decided: yes, quietly.) Does the asker see "Not this time", or is a decline invisible to them (the request just disappears)? Recommendation: show it quietly, so they stop waiting; wording to be tested.
2. (Decided: no.) Should asking automatically heart the game (a wish, U10) for the asker once it lands on the shelf? Recommendation: no, separate intents; the shelf heart stays one tap away.
3. Tune the caps (3 per asker per day, 5 per owner per day) after the first game day.

## Criteria

- [ ] A joined player can ask another joined player to bring a game in the owner's collection (`BringRequested`; decider + given/when/then test for each rejection and the no-op)
- [ ] The owner sees open requests on the bring page and can bring it (open requests for that game are answered) or decline
- [ ] The asker sees their open requests, can take one back, and sees when it is answered or declined
- [ ] Caps (3 standing requests per asker per day, 5 per owner per day; taking one back frees both), enforced in the decider's query (concurrent asks can't slip past the cap: test with two interleaved appends)
- [ ] Ticking the game anywhere answers the open requests (projection test)
- [ ] The hub's next step and the readiness view mention waiting requests
- [ ] Nobody outside the game day can ask or see requests; closed and cancelled game days are read-only (web tests)
- [ ] The collection page has the opt-out switch and tells owners who can see their collection; opted-out players can't be asked (decider + web test)
- [ ] Event evolution: new types only; documented in `CLAUDE.md` (vocabulary, events list, tag convention unchanged)
- [ ] Design mockup in `docs/design/` and the screens use existing components
