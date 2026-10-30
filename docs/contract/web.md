# Web

Prefix: `WEB`

What every page promises, whatever the feature.

## Wording and forms

- **WEB-1** Every rejection reaches people as its own plain sentence.
- **WEB-2** A refused form comes back with the reason and what was typed: as the form for htmx, as a full page for a plain post.
- **WEB-3** A form that succeeds sends htmx to the next page with `HX-Redirect`, and a plain post with a browser redirect.
- **WEB-4** Every page configures htmx so refusals are shown, not swallowed.
- **WEB-5** A page that isn't there answers not found with a page that says nothing about why and offers a way home.

## Theme

- **WEB-6** The chosen light or dark theme is applied before the page paints.
- **WEB-7** Every app bar ends with the light and dark toggle, signed-out pages included.

## Static files

- **WEB-8** Static files carry validators and are revalidated, so browsers reuse them only while they are unchanged.
- **WEB-9** After a deploy the browser gets the new static file, never a stale one.

## Components

- **WEB-10** Box art only loads from the web or our own static files, and a hostile image address can't break out of the page.
- **WEB-11** Values a button sends stay valid whatever characters they hold.
- **WEB-12** Avatars show initials made of whole characters, and an emoji alone doesn't count as a word.
- **WEB-13** A game's facts show its playtime range and player count, or its typical time when the range is wild.
- **WEB-14** A game's type shows as one or two telling words from its categories and mechanics.
- **WEB-15** A missing or unknown time zone falls back to one default everywhere.

## Running safely

- **WEB-16** Dev mode, with its in-page mailbox, refuses to start on a public or HTTPS address, bound beyond this machine, or with a real mail provider; production is unaffected.
