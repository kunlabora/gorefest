# U3 Absurd playtimes (7th Continent: 5-1000 min, Sleeping Gods: 60-1200 min)

Source: user testing, 2026-10-06. Status: ✅

**Feedback:** The 7th Continent shows 5-1000 minutes and Sleeping Gods 60-1200 minutes.

**Cause:** BGG's min/max playtime for campaign games is the length of a whole campaign or a single session extreme.

**Wanted:** a sane display and a sane time-budget filter: show BGG's typical playing time (`playingtime`) when the min-max range is wildly wide, and use it (not the max) for "fits the time budget". Campaign games should not pass or fail a 2-hour budget because of a 1000 minute max.

**Criteria**
- [x] A rule for "implausible range" in `core` (tested)
- [x] Game facts show the typical time for such games
- [x] Time-budget filtering uses the same rule

**Rule:** `Game.hasWildPlaytimeRange` (max over 8 times the min and over 240 minutes, so 20-120 or 30-180 stay ranges). Such a game counts as `Game.typicalMinutes` (BGG's `playingtime` when it lies inside the range, since it usually just repeats the max; else the geometric mean of min and max for games cached before we stored it), for the facts line ("~120 min") and for `Matcher.estimatedMinutes` (so the time budget, the group speed and the "nothing fits" relax options all use it). Games cached earlier show the geometric-mean fallback until their metadata is refreshed. Tests: `MatcherTest`, `ComponentsTest`, `BggXmlTest`.
