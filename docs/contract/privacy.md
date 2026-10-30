# Privacy

Prefix: `PR`

What the app promises the people it holds data about: erasure, their data file, retention, logs, the notice, and staying out of sight.

## Erasure

- **PR-1** Erasing an account removes the person's email, name, sessions, collection, BGG link and login attempts, and leaves everyone else's data alone.
- **PR-2** The event log is never rewritten: an erasure is one more event, and the ids left in the log point at nobody.
- **PR-3** Erasing someone twice, or someone who never registered, changes nothing.
- **PR-4** After an erasure the same email address can sign up again, as a new person.
- **PR-5** An erased player's BGG username is free for someone else to link.
- **PR-6** An erased player leaves the tables of game days still to be played; game days that are over keep their history.
- **PR-7** On game days still to be played, an erased player's games and wishes leave the shelf and their bring requests, asked or asked of them, go away; closed game days keep them as history.
- **PR-8** An erased player leaves every game night and date poll, and a date poll whose host was erased is over.
- **PR-9** Anyone without a name, such as an erased player, shows as "Someone".
- **PR-10** Every erasure is announced to the admin by id only, and re-applying erasures after a backup restore erases those accounts again.

## Download my data

- **PR-11** A person's data file holds their profile, their collection and what they did, in words.
- **PR-12** A person's data file never holds another person's id, or anything only that other person chose to share, such as a place.

## Retention

- **PR-13** After 24 months without signing in a person is warned by mail, and 30 days later their account is erased, unless they signed in.
- **PR-14** Someone hosting a game day still to come is not erased for being inactive, and neither is the admin.
- **PR-15** Erasing inactive accounts can be switched off.
- **PR-16** Expired sessions and login attempts older than a day are purged.

## Logs

- **PR-17** No login token, code, email address, invite or poll link token, or return path reaches the log; a request line keeps the route and drops the query.
- **PR-18** A mail the provider refused is logged by its status only, not by what the provider echoed back.

## Notices to the admin

- **PR-19** Notices to the admin carry a name and a link, never an email address.
- **PR-20** A Discord post to the admin can't ping anyone, and only a Discord webhook address is accepted.
- **PR-21** When the admin's Discord channel fails, the admin hears by mail instead.

## The privacy notice

- **PR-22** The privacy notice at `/privacy` is public and covers who runs the app, what you can ask for, what it keeps, who sees what, who else handles it, cookies, and how long.
- **PR-23** Every page links to the privacy notice in its footer, the sign-in page included.
- **PR-24** The notice's contact, mail provider and admin channel follow the config: the admin's address, or "ask whoever invited you" without one; the mail provider in use; Discord or mail.
- **PR-25** The notice says what you can change yourself, what the admin channel gets, how long things are kept and who sees your collection.
- **PR-26** The notice names the cookies, and the app needs no cookie banner.

## Staying out of sight

- **PR-27** Search engines are told to stay away: `robots.txt` disallows everything except Slack's link preview, every response says noindex, and every page carries a robots meta tag.
- **PR-28** No page makes the browser call a third party for fonts.
