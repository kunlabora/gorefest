# U17 Link BGG by a message from a Boardfest bot account

Source: user testing, 2026-10-06. Status: 📋

**Feedback:** Change the BGG linking process: have a Boardfest bot account on BGG send a personal message to the person who wants to link, containing a link or code.

**Feasibility note:** the BGG XML API2 is read-only; it has no way to send geekmail, so this needs scripted web login to a bot account, which BGG's terms may not allow (we told them we only use the API with our token). Ask BGG before building.

**Today:** the user pastes a code in their profile's Website/Homepage field (S3, instructions improved 2026-10-06).

**Open questions:** ask BGG whether a bot may send messages; alternative: keep the code flow.
