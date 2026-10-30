# Shelf

Prefix: `SH`

What people bring to a game day, the games they really want to play, and asking someone else to bring a game.

## Bringing games

- **SH-1** A joined player brings a game to a game day by ticking it in their collection, and unticks it to take it off the shelf.
- **SH-2** Ticking a game that is already brought, or unticking one that isn't, changes nothing.
- **SH-3** Several players can bring the same game: the shelf shows it once with all its owners, and it stays on the shelf while one owner is left.
- **SH-4** A game that isn't in a player's collection can be found by search on the bring page and brought; it then stays listed on their bring page, without counting towards their collection.
- **SH-5** The bring page's search looks in the games we already know first, asks BGG only when that isn't enough, and remembers what BGG found.
- **SH-6** When BGG is down, the bring page's search still shows the games we know.
- **SH-7** A search that finds nothing says so, and a one-letter query doesn't search at all.
- **SH-8** The search box waits until typing pauses and shows a spinner while it searches.
- **SH-9** A player without a linked BGG account is told how to link one to see their collection; a linked player is told where to sync it.
- **SH-10** The bring page's sidebar counts the whole shelf, says how many games each player brings and how many I'm adding, and leads back to the game day.
- **SH-11** The bring page keeps its sidebar, collection list and wish list current as games are brought, found or hearted, without a page reload.
- **SH-12** The game day page shows the shelf with who brings each game, and how many games each player brings.
- **SH-13** The invite shows how many games are on the shelf.
- **SH-14** Only players who joined can bring games; to anyone else the bring page doesn't exist.
- **SH-15** Once a game day has ended or been cancelled, its shelf is read-only: nothing can be brought or taken off.

## Wishes

- **SH-16** Any joined player can heart a game on the shelf, whoever brought it, and take the heart back; each game shows how many people want it.
- **SH-17** A player has at most 3 hearts per game day: a fourth is refused with "You can pick 3 favourites: take one off first", taking one back frees a slot, and other people's hearts don't count.
- **SH-18** Only games on the shelf can be hearted ("Nobody's bringing that game"), only by joined players, and only while the game day is on.
- **SH-19** A game leaving the shelf takes its hearts with it, freeing those slots, and bringing it again starts with none; while somebody still brings it, the hearts stay.
- **SH-20** An owner taking their copy back drops their own heart, not other people's.

## Bring requests

- **SH-21** A joined player can ask another joined player to bring a game from that player's collection; a game the owner doesn't have can't be asked for.
- **SH-22** The ask screen lists the other members who accept requests, each with a collection to filter and ask from, and a search shows which members have a game.
- **SH-23** Asking again while the same request is open changes nothing; several people can ask for the same game; after a decline, asking again is a new request.
- **SH-24** Nobody can ask themselves, ask someone who hasn't joined, or ask for a game the owner already brings (it shows as "Bringing it").
- **SH-25** An asker has at most 3 requests standing per game day, answered and declined ones included; a fourth is refused saying why, taking one back frees the slot, and being asked doesn't use up their own asks.
- **SH-26** An owner can be asked at most 5 times per game day; taking a request back lowers that count, and the owner's own asks don't count towards it.
- **SH-27** Two people asking at the same moment can't push past either cap.
- **SH-28** The owner sees the open requests on the bring page, per game with who asks; bringing the game, with one tap there or by ticking it anywhere, answers every open request of that owner for it.
- **SH-29** "Not this time" quietly declines every asker of that game, and an owner can only decline requests made of them.
- **SH-30** The asker sees their requests and how they went ("Asked", "is bringing it", "Not this time"); only an open request can be taken back, only by its asker, and a withdrawn one is gone.
- **SH-31** The owner's game day hub puts a waiting request first, the home page tags it ("Asked to bring a game"), and the host's readiness view counts the open requests.
- **SH-32** Players accept requests by default and can opt out on the collection page, which says who can see their collection; once opted out they vanish from the ask screen and can't be asked ("They'd rather not be asked to bring games") until they opt in again.
- **SH-33** Only members see the ask screen and the requests, or can ask; on an ended game day asking, taking back and declining are refused and requests stay as they were; a cancelled game day's ask screen is gone.

## Erasure

- **SH-34** When a player is erased, a game day still to be played loses their games, hearts and bring requests (asked or asked of them), which stop counting against any limit; closed game days keep them as history.
