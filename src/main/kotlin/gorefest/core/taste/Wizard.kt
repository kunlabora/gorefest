package gorefest.core.taste

import gorefest.core.Basis
import gorefest.core.BggId
import gorefest.core.ComparisonRecorded
import gorefest.core.ComparisonResult
import gorefest.core.game.Game
import java.time.Instant

/** A question in the taste round. */
data class WizardPair(val a: Game, val b: Game, val basis: Basis)

/** Where the round stands; the shell carries it between requests in the form. */
data class RoundState(val step: Int = 0, val stableSteps: Int = 0, val asked: Set<Set<BggId>> = emptySet(), val top: List<BggId> = emptyList())

/** What the player knows about games, as far as we can tell. */
data class PlayerGames(val history: TasteHistory, val owned: Set<BggId>) {
    /** Declared status wins; otherwise we assume people have played what they own. */
    fun hasPlayed(game: BggId) = history.played[game] ?: (game in owned)

    /**
     * The player said they haven't played [game] and never told us what they'd rather try it against:
     * all we'd have is a guess from its features, so the matcher treats it as unknown to them.
     */
    fun isUnknownToThem(game: BggId) = history.played[game] == false && game !in history.comparedGames
}

/**
 * Picks taste-round pairs. The pool is the game day's games in order (shelf, host's collection,
 * the player's collection); every [anchorEvery]th pair includes a popular anchor from BGG's top ranks,
 * which gives everyone a shared reference even when the shelf is long.
 */
class Wizard(
    private val model: TasteModel = BradleyTerry(),
    private val poolSize: Int = 24,
    private val anchorEvery: Int = 5,
    private val minPairs: Int = 10,
    private val maxPairs: Int = 15,
) {
    val roundLength get() = maxPairs

    /**
     * The next pair. With [mustInclude], every pair has one of those games in it (a top-up round
     * asks about what's still uncertain, against anything else).
     */
    fun next(
        pool: List<Game>, anchors: List<Game>, player: PlayerGames, round: RoundState, now: Instant,
        mustInclude: Set<BggId>? = null,
    ): WizardPair? {
        val games = pool.distinctBy { it.id }.take(poolSize)
        val extra = anchors.filter { a -> games.none { it.id == a.id } }.take(poolSize)
        val anchorTurn = (round.step % anchorEvery == anchorEvery - 1 && extra.isNotEmpty()) || games.size < 2
        val candidates = if (anchorTurn) games + extra else games
        val anchorIds = extra.map { it.id }.toSet()

        val vibe = player.history.scores(model, Basis.VIBE, now)
        val played = player.history.scores(model, Basis.PLAYED, now)
        return pairs(candidates)
            .filter { (a, b) -> setOf(a.id, b.id) !in round.asked }
            .filter { (a, b) -> mustInclude == null || a.id in mustInclude || b.id in mustInclude }
            .filter { (a, b) -> !anchorTurn || games.size < 2 || a.id in anchorIds || b.id in anchorIds }
            .map { (a, b) ->
                val basis = if (player.hasPlayed(a.id) && player.hasPlayed(b.id)) Basis.PLAYED else Basis.VIBE
                val scores = if (basis == Basis.PLAYED) played else vibe
                val p = scores.pPrefers(a.id, b.id)
                WizardPair(a, b, basis) to p * (1 - p) * (scores[a.id].variance + scores[b.id].variance)
            }
            .maxByOrNull { it.second }?.first
    }

    /**
     * After a play: something to compare the game just played with. One of [candidates] the player
     * has played, whose PLAYED score we're least sure about.
     */
    fun partner(player: PlayerGames, justPlayed: BggId, candidates: List<Game>, now: Instant): Game? {
        val scores = player.history.scores(model, Basis.PLAYED, now)
        return candidates.filter { it.id != justPlayed && player.hasPlayed(it.id) }
            .maxWithOrNull(compareBy<Game> { scores[it.id].variance }.thenBy { it.name })
    }

    /** Games in [games] whose prediction for this player is still unsure (variance above [threshold]). */
    fun uncertain(player: PlayerGames, games: List<Game>, now: Instant, threshold: Double = 0.6): Set<BggId> {
        val vibe = player.history.scores(model, Basis.VIBE, now)
        val played = player.history.scores(model, Basis.PLAYED, now)
        return games.filter { g -> (if (player.hasPlayed(g.id)) played else vibe)[g.id].variance > threshold }.map { it.id }.toSet()
    }

    /**
     * The oldest decisive answer older than [olderThan] whose pair wasn't answered again since:
     * worth asking again as tastes drift. Pairs re-answered recently are skipped, so the next one comes up.
     */
    fun stalePair(player: PlayerGames, games: (BggId) -> Game?, now: Instant, olderThan: java.time.Duration = java.time.Duration.ofDays(180)): WizardPair? {
        val all = player.history.comparisons
        fun age(c: ComparisonRecorded) = java.time.Duration.between(c.at, now)
        val recentPairs = all.filter { age(it) <= olderThan }.map { setOf(it.a, it.b) }.toSet()
        return all.asSequence()
            .filter { age(it) > olderThan && it.result != ComparisonResult.BOTH_GREAT && it.result != ComparisonResult.BOTH_MEH }
            .filter { setOf(it.a, it.b) !in recentPairs }
            .sortedBy { it.at }
            .firstNotNullOfOrNull { old ->
                val a = games(old.a) ?: return@firstNotNullOfOrNull null
                val b = games(old.b) ?: return@firstNotNullOfOrNull null
                WizardPair(a, b, old.basis)
            }
    }

    fun shouldStop(round: RoundState) = round.step >= maxPairs || (round.step >= minPairs && round.stableSteps >= 3)

    /**
     * After an answer: one step further, and whether the player's top 3 stayed put. Only an answer
     * that taught us something ([learned]) is a step; "haven't played" and "skip" just retire the pair,
     * so a finished round always holds as many comparisons as it has steps.
     */
    fun advance(round: RoundState, pair: WizardPair, newTop: List<BggId>, learned: Boolean = true) = round.copy(
        step = if (learned) round.step + 1 else round.step,
        stableSteps = if (!learned) round.stableSteps else if (newTop == round.top) round.stableSteps + 1 else 0,
        asked = round.asked + setOf(setOf(pair.a.id, pair.b.id)),
        top = newTop,
    )

    private fun pairs(games: List<Game>) = games.indices.flatMap { i -> (i + 1 until games.size).map { games[i] to games[it] } }
}

/** A predicted favourite and why. */
data class Favourite(val game: Game, val score: Score, val reason: FavouriteReason)

/** Why a game is among someone's likely favourites, from their own answers. The web layer words it. */
sealed interface FavouriteReason {
    data object CalledItGreat : FavouriteReason

    /** It won against these games, in the order they were answered. */
    data class PickedOver(val games: List<String>) : FavouriteReason

    /** It hasn't won a pair yet: it's ranked on thin evidence. */
    data object JustAGuess : FavouriteReason
}

/**
 * Predicted enjoyment: PLAYED scores for games the player has played (falling back to VIBE when
 * they never compared it after playing), VIBE scores (appeal) for the rest.
 */
class Predictor(private val model: TasteModel = BradleyTerry()) {
    /**
     * Played games: the PLAYED model. Unplayed games: appeal (VIBE) blended with what the PLAYED
     * model expects, weighted by how well this player's vibes have predicted their enjoyment.
     */
    fun enjoyment(player: PlayerGames, games: List<Game>, now: Instant): Map<BggId, Score> {
        val vibe = player.history.scores(model, Basis.VIBE, now)
        val played = player.history.scores(model, Basis.PLAYED, now)
        val hasPlayedData = played.byGame.isNotEmpty()
        val trust = vibeTrust(player.history, model, now)
        return games.associate { g ->
            g.id to when {
                player.hasPlayed(g.id) && g.id in played.byGame -> played[g.id]
                !hasPlayedData -> vibe[g.id]
                else -> {
                    val a = vibe[g.id]
                    val e = played[g.id]
                    Score(trust * a.mean + (1 - trust) * e.mean, trust * a.variance + (1 - trust) * e.variance)
                }
            }
        }
    }

    fun favourites(player: PlayerGames, games: List<Game>, now: Instant, count: Int = 3): List<Favourite> {
        val scores = enjoyment(player, games, now)
        val byId = games.associateBy { it.id }
        return games.distinctBy { it.id }
            .filter { it.id in player.history.comparedGames }
            .sortedByDescending { scores.getValue(it.id).mean }
            .take(count)
            .map { Favourite(it, scores.getValue(it.id), reason(it, player, byId)) }
    }

    private fun reason(game: Game, player: PlayerGames, names: Map<BggId, Game>): FavouriteReason {
        val mine = player.history.comparisons.filter { it.a == game.id || it.b == game.id }
        if (mine.any { it.result == ComparisonResult.BOTH_GREAT }) return FavouriteReason.CalledItGreat
        val beaten = mine.mapNotNull { c ->
            when {
                c.a == game.id && c.result == ComparisonResult.A -> c.b
                c.b == game.id && c.result == ComparisonResult.B -> c.a
                else -> null
            }
        }.distinct().mapNotNull { names[it]?.name }
        return if (beaten.isEmpty()) FavouriteReason.JustAGuess else FavouriteReason.PickedOver(beaten)
    }
}
