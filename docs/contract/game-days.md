# Game days

Prefix: `GD`

## Creating and editing

- **GD-1** Any signed-in player can host a game day with a title, date and place, lands on its page, and is its first player.
- **GD-2** A game day needs a title, a date that isn't in the past and a time zone that exists, and its title and place have a maximum length.
- **GD-3** A refused create or edit form says why and keeps what was typed, also without htmx.
- **GD-4** The create form comes prefilled with a fun title of two or three words ending in a party word, which the host can re-roll to a different one; editing keeps the host's own title.
- **GD-5** While typing, the create form previews the invite, and says the place is shown only to people who have joined.
- **GD-6** Only the host changes a game day's title, date and place, and everyone sees the new details right away.
- **GD-7** Editing follows the same rules as creating, saving without changes changes nothing, and a game day whose date has passed can still be retitled as long as its date stays.
- **GD-8** An ended game day can't be edited or joined, and its invite link can't be replaced.

## Invite links and joining

- **GD-9** Every member sees the invite link to copy; only the host can replace it, and the old link then stops working.
- **GD-10** Invite links are signed: a tampered link, one signed with another key, or a date poll's link doesn't open a game day.
- **GD-11** An invite link works until the game day ends; after that it says the game day has ended, also to someone who was signing up through it.
- **GD-12** The invite landing page shows the host, title, date, how many are going and the shelf, each game with who brings it, without logging in.
- **GD-13** The place stays off the invite landing page, its preview text and its image until you have joined; members see it on the hub.
- **GD-14** The invite link previews in chat apps with the title, date, how many are going and the shelf size, and an image that stays inside its canvas even for an endless title.
- **GD-15** A logged-out visitor signs in on the invite itself and lands joined on the game day, whether new or returning, and even when the magic link opens in another browser.
- **GD-16** A logged-in player joins with one button and lands on the game day; joining twice changes nothing, and a member opening the link goes straight to the hub.
- **GD-17** A crafted or someone else's join marker can't join anyone; it only shows the landing page.
- **GD-18** People joining at the same time never block each other.

## Members only

- **GD-19** Game day pages are members-only: a player who hasn't joined, or an unknown game day, gets a not-found for the page, editing, the invite and cancelling, never a refusal that confirms the game day exists.

## Hub and home

- **GD-20** The hub shows the title, date, place and how many are going, and lists the players by initials and name, with what they bring, "You" and the host; a player without a name shows as "Someone".
- **GD-21** The home page shows only game days I host or joined: the next one as the hero with the one step I still owe it, later ones under Coming up, and past ones latest first.
- **GD-22** With no game days, the home page explains how to host one or find a date.

## Readiness

- **GD-23** Only the host sees the readiness view: days to go, who joined, the shelf size, how many have a known taste, and the table sizes fewer than three shelf games serve.
- **GD-24** Readiness lists the players who need a nudge first, with their answer counts, and the host can copy a ready-made nudge that links to the taste round.

## Cancelling

- **GD-25** The host can cancel a game day that hasn't ended, after a confirm step, and only once; anyone else gets a not-found and the game day stays on.
- **GD-26** Members see a cancelled game day marked "Cancelled" on its hub and under Past on the home page, even with its date ahead.
- **GD-27** A cancelled game day is read-only: no joining, editing, new invite link, bringing or unbringing, wishes or logging plays, and only its hub can be opened.
- **GD-28** A cancelled game day's invite link says it was cancelled and refuses joining, while a member opening it lands on the hub.
- **GD-29** A cancelled game day stays cancelled: it never auto-closes, and a later end doesn't turn it into an ended one.

## Ending and the recap

- **GD-30** A game day ends by itself at midnight after its date, in its own time zone, and not a minute before; it ends only once.
- **GD-31** When a game day ends, everyone on it gets one recap mail saying who played what for how long, with the totals.
- **GD-32** In the recap, a game without a known name shows as "A game" and a player without a name as "someone".
- **GD-33** One recipient's mail failing doesn't stop the others getting their recap, and one game day failing to close doesn't hold up the others.
- **GD-34** After a game day ends its shelf is closed, while a forgotten play can still be logged.
