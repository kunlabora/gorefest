# BGG

Prefix: `BGG`

What Boardfest promises about BoardGameGeek: linking an account, collections, game details, search, how we treat BGG, and the fake BGG in dev.

## Linking an account

- **BGG-1** A player links a BGG account by putting a one-time code (`GORE-` and six characters) in any field of their BGG profile; until BGG shows the code there, the account stays unlinked and the page says so.
- **BGG-2** An unknown or empty BGG username is explained instead of getting a code.
- **BGG-3** The link page sits under Collection in the nav, can be skipped with "Not now", and shows the code to copy, three numbered steps, a picture of where the code goes on the profile, and a way back to change the username.
- **BGG-4** A BGG account belongs to one player at a time, whatever the case of the username; nobody else can link it.
- **BGG-5** A player has one linked account: linking the same one again changes nothing, and a different one is refused until they unlink.
- **BGG-6** The event log holds only a keyed hash of a BGG username, never the name itself.
- **BGG-7** Unlinking forgets the account and its collection, and frees the username for anyone, the player included.
- **BGG-8** An erased player's BGG username is free for someone else to link.
- **BGG-9** After linking, the player lands on their collection, which offers the way back to where they were going.
- **BGG-10** Until a player has linked an account, the home page suggests linking, and the collection and bring pages send them to link one.

## Collections

- **BGG-11** A sync stores the games a player owns on BGG, with their details; games removed on BGG disappear on the next sync.
- **BGG-12** When BGG says a collection is still being prepared, the sync keeps retrying until it is ready.
- **BGG-13** A sync fails with a reason when BGG doesn't know the user, and at once when BGG rejects our token; the collection page then offers to try again.
- **BGG-14** Relinking to another username while a sync is running ends with the new account's collection.
- **BGG-15** Collections older than a week are due for a refresh.
- **BGG-16** The collection page shows progress while importing, then lists the games alphabetically with their facts.

## Game details and search

- **BGG-17** Game details are fetched from BGG once per game, for everyone, not per player.
- **BGG-18** Cached game details survive a restart, and a refresh updates the games we hold without adding others.
- **BGG-19** From BGG we keep a game's player counts, its best and recommended player counts, playtime, weight, mechanics, categories and rank; an unranked game has no rank, and a campaign game keeps BGG's typical playing time.
- **BGG-20** Search looks in our own cache first and asks BGG only when that comes up short; games found on BGG are cached.
- **BGG-21** When BGG is down, search still shows what we have and says BGG is unavailable.
- **BGG-22** A search BGG had nothing for isn't asked again for ten minutes, and a failed one for about a minute.
- **BGG-23** A one-letter query doesn't search, and a search that finds nothing says so.

## Treating BGG well

- **BGG-24** All calls to BGG go through one paced queue: after a small burst, calls are spaced out.
- **BGG-25** Live searches jump ahead of routine refreshes in that queue.
- **BGG-26** Someone who stops waiting doesn't use up a call.
- **BGG-27** When BGG hangs, fails or the queue is full, the caller hears that BGG is unavailable instead of waiting forever.
- **BGG-28** A BGG response that declares a DOCTYPE is refused, so it can't make us read local files.
- **BGG-29** Every page credits BGG with the "Powered by BGG" logo, linked to boardgamegeek.com, in its footer.

## Dev mode

- **BGG-30** Without a BGG token, a fake BGG answers: every collection holds fifteen games plus every game with cover art.
- **BGG-31** Every dev game has cover art we ship, and every shipped cover belongs to a dev game.
- **BGG-32** In dev mode, the link page can fill in the fake profile with the code.
