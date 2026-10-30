# Accounts

Prefix: `ACC`

Signing in, sessions, account approval, the account page and changing an email address.

## Signing in

- **ACC-1** Pages that need a player send someone who isn't logged in to log in, and bring them back to where they were going afterwards; an htmx request comes back to the page it was made on.
- **ACC-2** Logging in and signing up are one step, and the login page says so and explains the flow, except to someone arriving through an invite.
- **ACC-3** Asking with an email address sends one mail holding a 6-digit code, also in the subject, and a link, and the code page shows where the mail went.
- **ACC-4** The link opens a confirm page and logs in only on a tap, so a mail scanner opening it can't use it up.
- **ACC-5** A code or a link logs in once, and using one kills the other.
- **ACC-6** Codes and links expire after 15 minutes.
- **ACC-7** Five wrong codes lock the attempt, even for the right code and the link.
- **ACC-8** At most five login mails go to one email and twenty to one internet address per hour, even when requests arrive at once; a mail that failed to send doesn't count.
- **ACC-9** A refused email address re-renders the form with what was typed, keeping an invite's own wording, and "send it again" mails a fresh code for the same email and destination.
- **ACC-10** The first login registers the player and asks their name, which can't be left empty; later logins only log in.
- **ACC-11** Someone already logged in skips the login page, and the way back after logging in can only point inside the app.
- **ACC-12** Login tokens, codes and session tokens are only stored hashed.
- **ACC-13** Logging out ends the session, also for a copied cookie, and pages are never stored by the browser, so Back after logging out shows nothing.
- **ACC-14** In dev mode mail goes to a dev mailbox that the code page points at; outside dev mode there is no dev mailbox.
- **ACC-15** Login mail goes out through the mail provider the config names: Scaleway TEM, Postmark or Resend.

## Account approval

- **ACC-16** A new account waits on the waiting page, wherever it goes, until the admin approves it; an approved player never sees that page.
- **ACC-17** The admin approves or rejects a newcomer, who gets a mail either way; rejecting deletes the account.
- **ACC-18** Only the admin sees and works the approvals and the account requests; everyone else gets a 404.
- **ACC-19** The admin's own email is approved at its first login.
- **ACC-20** Someone waiting for approval can't join a game day through an invite.
- **ACC-21** Outside dev mode the app won't start without an admin email.
- **ACC-22** A notice to the admin that fails never stops someone from signing up.

## Account page

- **ACC-23** The account page shows your name and email address, needs a login, and is linked from the app bar.
- **ACC-24** From the account page a person asks the admin to delete their account or send their data; the page shows it was asked, and asking again doesn't tell the admin twice.
- **ACC-25** A host asking to delete their account is told that their upcoming game days stay.
- **ACC-26** When the admin erases an account, the person gets a mail and their old session stops working.
- **ACC-27** The admin downloads a person's data as a file the browser doesn't keep, and a data request stays open until the admin marks it sent.
- **ACC-28** The admin's delete confirmation shows any name as plain text, quotes and backslashes included.

## Changing an email address

- **ACC-29** A person changes their own email: a code goes to the new address, the email changes only once that code is entered, the old address is told, and they stay logged in.
- **ACC-30** Wrong codes are refused and five lock the change, even for the right code; the last try is worded in the singular.
- **ACC-31** An address someone else already has looks the same as any other and gets no mail, so nobody learns whether an email has an account.
- **ACC-32** A malformed or unchanged address is explained, and change codes are rationed: a sixth one in a row is refused.
- **ACC-33** The admin can't change the address the admin is known by.
