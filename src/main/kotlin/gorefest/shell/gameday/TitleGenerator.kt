package gorefest.shell.gameday

import kotlin.random.Random

/**
 * Fun default titles for a new game day: "Dice Fest", "Meeple Mayhem Party".
 * Randomness comes from the injected [random], so a seeded one gives the same titles every time.
 */
class TitleGenerator(private val random: Random = Random.Default) {
    /** A title that isn't [not], so "another one" always changes something. */
    fun next(not: String? = null): String {
        repeat(MAX_TRIES) {
            val title = roll()
            if (title != not) return title
        }
        // Practically unreachable: the vocabulary has hundreds of combinations.
        return roll()
    }

    private fun roll(): String {
        val start = STARTS.random(random)
        val end = ENDS.random(random)
        return if (random.nextBoolean()) "$start $end" else "$start ${MIDDLES.random(random)} $end"
    }

    companion object {
        private const val MAX_TRIES = 10

        val STARTS = listOf(
            "Meeple", "Dice", "Cardboard", "Cube", "Worker", "Tile", "Deck", "Wooden", "Shuffle", "Snack", "Dungeon", "Cardstock",
        )
        val MIDDLES = listOf("Mayhem", "Madness", "Mischief", "Chaos", "Shenanigans", "Trouble", "Tactics", "Rampage")
        val ENDS = listOf("Party", "Fest", "Night", "Bash", "Jam", "Showdown", "Summit", "Marathon", "Gathering")
    }
}
