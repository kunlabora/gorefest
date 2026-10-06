# U1 Joining the game day straight after signing up

Source: user testing, 2026-10-06. Status: ✅

**Feedback:** When receiving an invite as an unregistered player, after "registering" and saying "this is me", I expect to immediately join the game day I was invited to.

**Today:** the invite stays pending (or is lost) after the login, and the player has to tap the invite link again.

**Wanted:** the invite survives registration/login (code or magic link, same device or not) and the player lands joined on the game day (or on the date poll for a poll invite).

**Criteria**
- [x] New player: invite link → email → code or magic link → joined, landing on the game day hub
- [x] Returning player who isn't logged in: same
- [x] Works when the magic link opens in another browser than the invite link (the invite is carried through the login, not only a cookie)
- [x] Web test for each path

**Done:** Join straight after signing up: the invite path carries `?join=1` through `/login` (stored in the login attempt, so it works across browsers) and `/welcome`; `GET /i/{token}?join=1` and `GET /p/{token}?join=1` join a logged-in, named player and redirect to the hub or poll.
