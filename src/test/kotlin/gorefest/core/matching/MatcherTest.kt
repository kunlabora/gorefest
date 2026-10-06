package gorefest.core.matching

import gorefest.core.Basis
import gorefest.core.BggId
import gorefest.core.ComparisonRecorded
import gorefest.core.ComparisonResult
import gorefest.core.PlayerId
import gorefest.core.game.Game
import gorefest.core.gameday.ShelfGame
import gorefest.core.taste.FeatureModel
import gorefest.core.taste.PlayerGames
import gorefest.core.taste.Predictor
import gorefest.core.taste.TasteHistory
import gorefest.shell.bgg.DevCatalog
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldNotContain
import io.kotest.matchers.types.shouldBeInstanceOf
import org.junit.jupiter.api.Test
import java.time.Instant
import kotlin.uuid.Uuid

class MatcherTest {
    private val now = Instant.parse("2026-09-24T10:00:00Z")
    private val matcher = Matcher()
    private fun g(name: String) = DevCatalog.games.first { it.name == name }
    private val azul = g("Azul")                    // 2–4, 30–45
    private val brass = g("Brass: Birmingham")      // 2–4, 60–120
    private val codenames = g("Codenames")          // 2–8, 15
    private val ti4 = g("Twilight Imperium: Fourth Edition") // 3–6, 240–480

    private val owner = PlayerId(Uuid.random())
    private fun shelf(vararg games: Game, eager: Map<Game, PlayerId> = emptyMap()) =
        games.map { it to ShelfGame(it.id, listOf(owner), eager[it]?.let(::setOf).orEmpty()) }

    /** A player who prefers games earlier in [likes] over later ones, answered [times] times. */
    private fun player(name: String, vararg likes: Game, times: Int = 3, played: Boolean = false, id: PlayerId = PlayerId(Uuid.random())): TablePlayer {
        val comparisons = likes.toList().zipWithNext().flatMap { (a, b) ->
            List(times) { ComparisonRecorded(id, a.id, b.id, ComparisonResult.A, if (played) Basis.PLAYED else Basis.VIBE, now) }
        }
        val playedGames = if (played) likes.associate { it.id to true } else emptyMap()
        return TablePlayer(id, name, PlayerGames(TasteHistory(comparisons, playedGames), emptySet()))
    }

    private fun Suggestions.names() = (this as Suggestions.Found).top.map { it.game.name }

    @Test
    fun `games must suit the table size and the time budget`() {
        val table = List(3) { player("P$it") }

        matcher.suggest(table, shelf(azul, brass, codenames, ti4), timeBudget = 60, now).names().toSet() shouldBe setOf("Azul", "Codenames")
        matcher.suggest(table + player("P4") + player("P5"), shelf(azul, codenames), null, now).names() shouldBe listOf("Codenames")
    }

    @Test
    fun `one miserable player sinks a game that others love`() {
        val table = listOf(player("Anna", brass, azul), player("Bram", brass, azul), player("Kim", azul, brass, times = 6))

        val top = matcher.suggest(table, shelf(azul, brass), null, now).names()

        top.first() shouldBe "Azul"
    }

    @Test
    fun `an eager owner at the table gets their game, with a reason naming them`() {
        val kim = player("Kim")
        val result = matcher.suggest(listOf(kim, player("Bram")), shelf(azul, brass, eager = mapOf(brass to kim.id)), null, now) as Suggestions.Found

        result.top.first().game shouldBe brass
        result.top.first().reason shouldBe SuggestionReason.EagerOwner("Kim")
    }

    @Test
    fun `games nobody played get a novelty bonus and say so`() {
        val table = listOf(player("Anna", azul, brass, times = Matcher.KNOWN_AFTER, played = true), player("Bram"))

        val result = matcher.suggest(table, shelf(azul, codenames), null, now) as Suggestions.Found

        result.top.first { it.game == codenames }.reason shouldBe SuggestionReason.NewAndLiked
    }

    @Test
    fun `players with too few answers are reported, and the picks only talk about fit when nobody is known`() {
        val bram = player("Bram")
        val strangers = matcher.suggest(listOf(bram, player("Kim")), shelf(azul, brass), null, now) as Suggestions.Found

        strangers.unknown.size shouldBe 2
        // Without anyone's taste, no claim about enjoyment.
        strangers.top.map { it.reason }.none { it == SuggestionReason.NewAndLiked || it is SuggestionReason.SafeBet || it == SuggestionReason.GoodMiddleGround } shouldBe true
        strangers.top.single { it.game == azul }.reason shouldBe SuggestionReason.BestAtTableSize(2)
        strangers.top.single { it.game == brass }.reason shouldBe SuggestionReason.NobodyPlayedIt

        val mixed = matcher.suggest(listOf(player("Anna", azul, brass, times = Matcher.KNOWN_AFTER), bram), shelf(azul), null, now) as Suggestions.Found
        mixed.unknown shouldBe listOf(bram.id)
    }

    @Test
    fun `when everyone who played it rated it highly, it's a safe bet`() {
        val table = List(4) { player("P$it", azul, brass, codenames, times = Matcher.KNOWN_AFTER, played = true) }

        val result = matcher.suggest(table, shelf(azul), null, now) as Suggestions.Found

        result.top.single().reason shouldBe SuggestionReason.SafeBet(4)
    }

    @Test
    fun `nothing fitting in time suggests a longer budget`() {
        val result = matcher.suggest(List(4) { player("P$it") }, shelf(brass), timeBudget = 45, now)

        result shouldBe Suggestions.NothingFits.NoGameInTime(tableSize = 4, budget = 45, relaxTo = 120, fitsThen = 1)
    }

    @Test
    fun `nothing for this many players says so`() {
        val result = matcher.suggest(List(7) { player("P$it") }, shelf(azul, brass), null, now)

        result shouldBe Suggestions.NothingFits.NoGameForTableSize(7)
    }

    @Test
    fun `duration follows the player count, rounded to 5 minutes, scaled by group speed`() {
        Matcher.estimatedMinutes(brass, 2) shouldBe 60
        Matcher.estimatedMinutes(brass, 3) shouldBe 90
        Matcher.estimatedMinutes(brass, 4) shouldBe 120
        Matcher.estimatedMinutes(brass, 4, speed = 1.25) shouldBe 150
        Matcher.estimatedMinutes(g("Twilight Struggle"), 2) shouldBe 150
        BggId(1).value shouldBe 1
    }


    @Test
    fun `a game without a playtime is assumed to take an hour, not a few minutes`() {
        val mystery = codenames.copy(id = BggId(999_002), name = "Mystery", minPlaytime = 0, maxPlaytime = 0)
        val table = List(3) { player("P$it") }

        Matcher.estimatedMinutes(mystery, 3) shouldBe Matcher.UNKNOWN_MINUTES
        Matcher.estimatedMinutes(mystery, 3, speed = 1.5) shouldBe 90
        matcher.suggest(table, shelf(mystery, azul), timeBudget = 45, now).names() shouldBe listOf("Azul")
        matcher.suggest(table, shelf(mystery), timeBudget = 90, now).names() shouldBe listOf("Mystery")
    }

    @Test
    fun `a game a player said they haven't played is no more than a novelty, whatever its features say`() {
        val featureMatcher = Matcher(Predictor(FeatureModel(games = { id -> DevCatalog.games.firstOrNull { it.id == id } })))
        val cascadia = g("Cascadia") // shares its mechanics with Azul
        val id = PlayerId(Uuid.random())
        val likesCascadia = List(Matcher.KNOWN_AFTER) { ComparisonRecorded(id, cascadia.id, brass.id, ComparisonResult.A, Basis.VIBE, now) }
        fun kim(declared: Map<BggId, Boolean>) = TablePlayer(id, "Kim", PlayerGames(TasteHistory(likesCascadia, declared), emptySet()))

        val guessed = featureMatcher.suggest(listOf(kim(emptyMap())), shelf(azul.copy(minPlayers = 1, bestPlayers = setOf(1, 2, 3))), null, now) as Suggestions.Found
        val declared = featureMatcher.suggest(listOf(kim(mapOf(azul.id to false))), shelf(azul.copy(minPlayers = 1, bestPlayers = setOf(1, 2, 3))), null, now) as Suggestions.Found

        // Without a declaration the features lift Azul well above an average game ...
        (guessed.top.single().score > 0.3 + 0.1) shouldBe true
        // ... but once Kim says they haven't played it, all it gets is the novelty bonus, and the reason says so.
        declared.top.single().score shouldBe 0.3
        declared.top.single().reason shouldBe SuggestionReason.NewToSome(listOf("Kim"))
    }

    @Test
    fun `a campaign game with a wild playtime range counts as its typical time, for any table size`() {
        val seventhContinent = codenames.copy(id = BggId(999_003), name = "The 7th Continent", minPlayers = 1, maxPlayers = 4, minPlaytime = 5, maxPlaytime = 1000, playingTime = 120)
        val brassLike = brass.copy(id = BggId(999_004), name = "Fat Euro", minPlaytime = 60, maxPlaytime = 300) // wide, but not wild

        seventhContinent.hasWildPlaytimeRange shouldBe true
        brassLike.hasWildPlaytimeRange shouldBe false
        Matcher.estimatedMinutes(seventhContinent, 1) shouldBe 120
        Matcher.estimatedMinutes(seventhContinent, 4) shouldBe 120
        Matcher.estimatedMinutes(seventhContinent, 3, speed = 1.5) shouldBe 180
        val table = List(3) { player("P$it") }
        matcher.suggest(table, shelf(seventhContinent), timeBudget = 120, now).names() shouldBe listOf("The 7th Continent")
        matcher.suggest(table, shelf(seventhContinent), timeBudget = 90, now).shouldBeInstanceOf<Suggestions.NothingFits.NoGameInTime>()
    }

    @Test
    fun `without BGG's typical time, a wild range falls back to the geometric mean`() {
        codenames.copy(minPlaytime = 5, maxPlaytime = 1000).typicalMinutes shouldBe 71
    }
}
