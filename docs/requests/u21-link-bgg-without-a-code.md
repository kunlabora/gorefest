# U21 Link BGG without a code

Source: a new player gave up while linking, 2026-11-17. Status: ✅ built

**Idea:** linking a BGG account is typing your BGG username. No code in the BGG profile, no second step.

**Today:** linking takes two steps (S3, BGG-1, BGG-3). The player types their username, gets a `GORE-` code, logs in to
BoardGameGeek, pastes the code into Contact Information → Website/Homepage (BGG asks for their password), comes back and
taps "I've added it". Only then does `LinkBggAccountDecider` record `BggAccountLinkedHashed`. A BGG account belongs to one
player at a time (BGG-4, `Rejection.BggAccountTaken`). A player gave up halfway: too much work for what it gives. U17 (a
bot sending the code by geekmail) was dropped on 2026-10-14.

## Behaviour

- The link page asks for the BGG username, with one button, **Link**. If BGG knows the name, the account is linked at
  once and the player lands on their collection, which starts importing (as today, BGG-9). An unknown or empty name is
  explained, and BGG being down too, as today (BGG-2).
- Several players may link the same BGG account. Partners or housemates who share one collection on BGG can both bring
  from it. Each gets their own copy of the collection.
- Nobody proves the account is theirs. Someone could link a friend's or a stranger's collection. BGG collections are
  public anyway, the group is invite-only, and the worst that follows is a wrong "bring from collection" list or a bring
  request to someone who doesn't have the game. Unlinking fixes it.
- Unchanged: one linked account per player; the same one again changes nothing, a different one waits until they
  unlink (BGG-5). Unlinking forgets the account and its collection (BGG-7).

## Design

**Events:** none new and none changed. `BggAccountLinkedHashed` still holds only the keyed hash of the username (BGG-6).

**Deciders:** `LinkBggAccountDecider` loses the uniqueness rule: its query shrinks to the player's own link, unlink and
erasure events, so two players linking the same account never conflict. `Rejection.BggAccountTaken` goes (nothing else
uses it). The command keeps `username` to recognise a link written before the hash (`BggAccountLinked`).

**Shell:** `BggVerification` loses `code` and `isOnProfile`; what is left is the keyed hash, so it becomes
`BggUsernameKey`. `BggUser.profileText` and the fake BGG's `putInProfile` go. The XML user parse keeps id and name.

**Web** (`web.bgg`): `POST /bgg/username` checks the name and links; `POST /bgg/verify`, `POST /dev/bgg/profile`, the
code step and `static/bgg-profile-code.webp` go. The page is the old step 1 without "step 1 of 2", in the login's hero
pattern (`signinField`, `actionButton`). It says plainly that we don't check the account is theirs.

**Privacy:** a member can link a BGG account that isn't theirs, so a non-member's public BGG username and collection can
end up in the app, held under the member's account (`collection_syncs`, `collections`) and erased with it. Nobody new
sees anything: a collection shows to its holder, and to members of the same game day on the ask screen (U20).
`docs/gdpr.md` says so. `PlayerErased` changes nothing here.

**Matcher:** no change.

## Open questions

None: decided with Tim on 2026-11-17 (no verification, no uniqueness, keep one account per player).

## Criteria

- [x] BGG-33: a player links a BGG account by typing its username; if BGG knows it, it's linked at once, without a code or a change on BGG (strikes BGG-1)
- [x] BGG-34: the link page sits under Collection in the nav, can be skipped with "Not now", and asks only for the username (strikes BGG-3)
- [x] BGG-35: several players can link the same BGG account, whatever the case of the username, each with their own collection (strikes BGG-4 and BGG-8)
- [x] BGG-7 loses "frees the username for anyone": unlinking forgets the account and its collection, and the player can link again
- [x] The code step, the verify route, the dev "fake profile" button and the profile picture are gone
- [x] No event changes; erasure unchanged (`PlayerErased` still ends the link)
- [x] BGG-36: the link page says that nobody checks the account is theirs, and that a household can share one
- [x] Mockup `docs/design/mockups/screen-bgg.html` shows the one-step page; the screen uses existing components
- [x] `CLAUDE.md` (BGG integration), `docs/gdpr.md` and the `/privacy` notice match (privacy-check)

Built as designed, with these notes:

- PR-5 ("an erased player's BGG username is free for someone else to link") was struck too; what is left of it is PR-29 (an erased player has no linked account). WEB-11 was struck: its only proof was the dev "put the code in the fake profile" button, and no button sends free text any more.
- The review added BGG-36 (the page says nobody checks) and a test that two players on one account keep their own collections when one unlinks.
- Known gaps (`docs/contract/README.md`): with a shared account, one player bringing a game doesn't answer a bring request to the other, and the game can still be asked of the other; after a `SECRET_KEY` rotation, linking the same account again is refused instead of changing nothing.
