# Taste

Prefix: `TA`

How Boardfest learns what each player likes from pairwise comparisons, and what the wizard asks to learn it.

## Comparisons and the taste model

- **TA-1** A comparison is always between two different games; comparing a game with itself is refused.
- **TA-2** Picking one game over another ranks it higher and predicts it would win again, and beating the winner puts a game on top.
- **TA-3** "Both great" lifts both games above average and "both meh" sinks both, by the same amount.
- **TA-4** What a player would rather try (`VIBE`) and what they enjoyed (`PLAYED`) are learned separately, so wanting to try a game never overrules not enjoying it.
- **TA-5** A `WATCHED` answer, given after watching a table play a game, teaches what the player would enjoy, not what they'd like to try, and does not make the game played.
- **TA-6** Recent answers count more than old ones, so a changed taste wins out over time.
- **TA-7** The more often a game is compared, the surer its score; a game never compared starts at average.
- **TA-8** Taste carries over to games a player never compared, through what those games are like (mechanics, categories, weight, length, best player counts), and a game whose details arrive later gets that benefit once they do.
- **TA-9** How much a player's "rather try" answers count depends on how well they predicted what that player enjoyed after playing; without that evidence they count half.
- **TA-10** The taste model in use predicts a player's answers better than its predecessor did, and far better than a coin flip, on a replay of their comparisons.
- **TA-11** Each player's taste keeps their own comparisons and what they said they have or haven't played.

## The wizard

- **TA-12** A member who hasn't done a taste round is pointed to it from the game day.
- **TA-13** Each step shows two games, each with what kind of game it is, and the answers: pick one, "Haven't played either", "Both meh", "Both great", and "Haven't played this" on each game, named for screen readers; never "Don't know".
- **TA-14** A pair the player has played (games they own count, unless they said they haven't played them) asks "Which did you enjoy more?"; any other pair asks "Which would you rather try?".
- **TA-15** Pairs come from the game day's shelf and never repeat within a round; about one in five adds a popular anchor game, and anchors top up a shelf too small to make pairs.
- **TA-16** The pair the model is least sure about is asked first, and games already well compared stop counting as uncertain.
- **TA-17** An answer is recorded and the next pair is dealt in place, with the progress shown as a step out of 15.
- **TA-18** "Haven't played" answers record no comparison but remember those games as unplayed for the player, and the round moves on to another pair.
- **TA-19** A first round ends after 15 answers, or from 10 once the likely top 3 stayed the same for 3 answers, or when no pairs are left; it never ends early while pairs remain, even for a player whose answers taught nothing.
- **TA-20** A finished round always leaves enough comparisons to count as a known taste, also with "haven't played" answers mixed in, and answering "haven't played" every time still ends it.
- **TA-21** Reloading or going back mid-round carries on where it was, without asking an answered pair again or derailing the round, and the payoff counts every answer.
- **TA-22** On a keyboard, A and B pick a game and 1 and 2 answer "both great" and "both meh", without reacting to shortcuts, held keys or typing in a field.
- **TA-23** The BGG credit stays in the page footer while pairs are swapped in.

## Payoff and favourites

- **TA-24** After the round, the payoff shows "Your likely favourites today": the three shelf games the player is predicted to like most, with links back to the game day and to another round.
- **TA-25** Each favourite comes with a reason from the player's own answers ("You picked it over …", called both great), or says it is just a guess so far.
- **TA-26** For games the player has played, favourites follow how much they enjoyed them rather than how much they wanted to try them.
- **TA-27** The payoff counts the answers in words ("1 answer", "12 answers") and leaves out a count that makes no sense.
- **TA-28** On the payoff the rank pills sit inside their cards, so nothing overlaps the bottom of the page on a phone.
- **TA-29** With an empty shelf, the payoff says so and points to bringing games.

## Coming back

- **TA-30** After a round, when shelf games are still uncertain, the game day offers a short top-up ("Sharpen your picks") of 5 pairs, each holding an uncertain game, against an anchor once the shelf pairs run out.
- **TA-31** A top-up first asks again a pair answered long ago and not asked since, one at a time.
- **TA-32** When the player knows the whole shelf well, the game day points to their favourites instead of another round.
