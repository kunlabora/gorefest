# U3 Absurd playtimes (7th Continent: 5-1000 min, Sleeping Gods: 60-1200 min)

Source: user testing, 2026-10-06. Status: 📋

**Feedback:** The 7th Continent shows 5-1000 minutes and Sleeping Gods 60-1200 minutes.

**Cause:** BGG's min/max playtime for campaign games is the length of a whole campaign or a single session extreme.

**Wanted:** a sane display and a sane time-budget filter: show BGG's typical playing time (`playingtime`) when the min-max range is wildly wide, and use it (not the max) for "fits the time budget". Campaign games should not pass or fail a 2-hour budget because of a 1000 minute max.

**Criteria**
- [ ] A rule for "implausible range" in `core` (tested)
- [ ] Game facts show the typical time for such games
- [ ] Time-budget filtering uses the same rule
