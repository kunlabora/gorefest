package gorefest.shell.bgg

import gorefest.core.BggId
import gorefest.core.bgg.Game
import java.util.concurrent.ConcurrentHashMap

/**
 * BGG stand-in for dev mode (no `BGG_TOKEN`) and tests. Every username exists; its collection is
 * a stable pick from [DevCatalog]. Profiles start empty, like a real one before you paste a code.
 */
class FakeBggApi(private val catalog: List<Game> = DevCatalog.games) : BggApi {
    private val profiles = ConcurrentHashMap<String, String>()
    private val collections = ConcurrentHashMap<String, Set<BggId>>()
    private val byId = catalog.associateBy { it.id }

    /** Usernames BGG doesn't know. */
    val missingUsers: MutableSet<String> = ConcurrentHashMap.newKeySet()

    /** What a player would do on boardgamegeek.com: paste text into a profile field. */
    fun putInProfile(username: String, text: String) {
        profiles[username.lowercase()] = text
    }

    fun setCollection(username: String, games: Set<BggId>) {
        collections[username.lowercase()] = games
    }

    override suspend fun user(name: String): BggUser? =
        if (name.isBlank() || name.lowercase() in missingUsers) null else BggUser(id = name.lowercase().hashCode(), name = name, profileText = profiles[name.lowercase()].orEmpty())

    override suspend fun collection(username: String): CollectionResult =
        CollectionResult.Ready(collections[username.lowercase()] ?: defaultCollection(username))

    override suspend fun things(ids: Collection<BggId>): List<Game> = ids.mapNotNull(byId::get)

    override suspend fun search(query: String): List<SearchHit> =
        catalog.filter { it.name.contains(query.trim(), ignoreCase = true) }.map { SearchHit(it.id, it.name, it.year) }

    /** 15 games, the same ones every time for the same username. */
    private fun defaultCollection(username: String): Set<BggId> {
        val seed = username.lowercase().hashCode()
        return catalog.sortedBy { (it.id.value * 31 + seed).hashCode() }.take(15).map { it.id }.toSet()
    }
}

/** Well-known games with approximate BGG data, for dev mode and tests. Not a substitute for the API. */
object DevCatalog {
    private fun game(
        id: Int, name: String, year: Int, players: IntRange, best: Set<Int>, time: IntRange, weight: Double, rank: Int?,
        mechanics: Set<String>, categories: Set<String>,
    ) = Game(
        id = BggId(id), name = name, year = year, minPlayers = players.first, maxPlayers = players.last,
        bestPlayers = best, recommendedPlayers = players.toSet() - best, minPlaytime = time.first, maxPlaytime = time.last,
        weight = weight, rank = rank, mechanics = mechanics, categories = categories,
    )

    private val euro = setOf("Economic")
    private val party = setOf("Party Game")

    val games: List<Game> = listOf(
        game(224517, "Brass: Birmingham", 2018, 2..4, setOf(3, 4), 60..120, 3.9, 1, setOf("Hand Management", "Network and Route Building", "Loans"), euro + "Industry / Manufacturing"),
        game(342942, "Ark Nova", 2021, 1..4, setOf(2), 90..150, 3.8, 2, setOf("Hand Management", "Tags", "Tableau Building"), setOf("Animals", "Economic")),
        game(174430, "Gloomhaven", 2017, 1..4, setOf(3), 60..120, 3.9, 4, setOf("Cooperative Game", "Campaign / Battle Card Driven", "Hand Management"), setOf("Adventure", "Fantasy", "Fighting")),
        game(233078, "Twilight Imperium: Fourth Edition", 2017, 3..6, setOf(6), 240..480, 4.3, 5, setOf("Area Majority / Influence", "Trading", "Voting"), setOf("Science Fiction", "Negotiation", "Political")),
        game(162886, "Spirit Island", 2017, 1..4, setOf(2), 90..120, 4.0, 11, setOf("Cooperative Game", "Area Majority / Influence", "Variable Player Powers"), setOf("Fantasy", "Mythology")),
        game(316554, "Dune: Imperium", 2020, 1..4, setOf(4), 60..120, 3.0, 3, setOf("Deck Building", "Worker Placement"), setOf("Science Fiction", "Political")),
        game(167791, "Terraforming Mars", 2016, 1..5, setOf(3), 120..120, 3.3, 8, setOf("Hand Management", "Tile Placement", "Tableau Building"), setOf("Science Fiction", "Economic", "Environmental")),
        game(187645, "Star Wars: Rebellion", 2016, 2..4, setOf(2), 180..240, 3.7, 12, setOf("Area Movement", "Dice Rolling", "Hidden Movement"), setOf("Science Fiction", "Movies / TV / Radio theme", "Wargame")),
        game(220308, "Gaia Project", 2017, 1..4, setOf(3), 60..150, 4.4, 13, setOf("Network and Route Building", "Variable Player Powers"), setOf("Science Fiction", "Economic", "Territory Building")),
        game(193738, "Great Western Trail", 2016, 2..4, setOf(3), 75..150, 3.7, 15, setOf("Deck Building", "Hand Management", "Point to Point Movement"), setOf("American West", "Animals", "Economic")),
        game(169786, "Scythe", 2016, 1..5, setOf(4), 90..115, 3.4, 16, setOf("Area Majority / Influence", "Engine Building", "Variable Player Powers"), setOf("Science Fiction", "Economic", "Territory Building")),
        game(182028, "Through the Ages: A New Story of Civilization", 2015, 2..4, setOf(3), 120..240, 4.4, 17, setOf("Card Drafting", "Action Points"), setOf("Civilization", "Economic", "Card Game")),
        game(237182, "Root", 2018, 2..4, setOf(4), 60..90, 3.7, 20, setOf("Area Majority / Influence", "Variable Player Powers", "Dice Rolling"), setOf("Animals", "Fantasy", "Wargame")),
        game(266192, "Wingspan", 2019, 1..5, setOf(3), 40..70, 2.4, 25, setOf("Engine Building", "Hand Management", "Dice Rolling"), setOf("Animals", "Card Game", "Educational")),
        game(312484, "Lost Ruins of Arnak", 2020, 1..4, setOf(3), 30..120, 2.9, 30, setOf("Deck Building", "Worker Placement"), setOf("Adventure", "Exploration")),
        game(124361, "Concordia", 2013, 2..5, setOf(4), 100..100, 3.0, 22, setOf("Hand Management", "Network and Route Building"), setOf("Ancient", "Economic", "Nautical")),
        game(120677, "Terra Mystica", 2012, 2..5, setOf(4), 60..150, 4.0, 36, setOf("Network and Route Building", "Variable Player Powers"), setOf("Civilization", "Economic", "Fantasy", "Territory Building")),
        game(31260, "Agricola", 2007, 1..4, setOf(3, 4), 30..150, 3.6, 43, setOf("Worker Placement", "Hand Management"), setOf("Animals", "Economic", "Farming")),
        game(12333, "Twilight Struggle", 2005, 2..2, setOf(2), 120..180, 3.6, 18, setOf("Area Majority / Influence", "Campaign / Battle Card Driven"), setOf("Modern Warfare", "Political", "Wargame")),
        game(199792, "Everdell", 2018, 1..4, setOf(2, 3), 40..80, 2.8, 40, setOf("Worker Placement", "Tableau Building", "Hand Management"), setOf("Animals", "Fantasy", "City Building")),
        game(2651, "Power Grid", 2004, 2..6, setOf(4, 5), 120..120, 3.3, 60, setOf("Auction / Bidding", "Network and Route Building"), euro + "Industry / Manufacturing"),
        game(3076, "Puerto Rico", 2002, 3..5, setOf(4), 90..150, 3.3, 58, setOf("Variable Phase Order", "Tile Placement"), setOf("City Building", "Economic", "Farming")),
        game(173346, "7 Wonders Duel", 2015, 2..2, setOf(2), 30..30, 2.2, 21, setOf("Card Drafting", "Set Collection"), setOf("Ancient", "Card Game", "City Building")),
        game(68448, "7 Wonders", 2010, 2..7, setOf(4, 5), 30..30, 2.3, 90, setOf("Card Drafting", "Simultaneous Action Selection"), setOf("Ancient", "Card Game", "City Building")),
        game(295947, "Cascadia", 2021, 1..4, setOf(3, 4), 30..45, 1.8, 50, setOf("Tile Placement", "Pattern Building", "Drafting"), setOf("Animals", "Environmental")),
        game(366013, "Heat: Pedal to the Metal", 2022, 1..6, setOf(5, 6), 30..60, 2.2, 28, setOf("Hand Management", "Race", "Push Your Luck"), setOf("Racing", "Sports")),
        game(230802, "Azul", 2017, 2..4, setOf(2, 3), 30..45, 1.8, 70, setOf("Pattern Building", "Tile Placement", "Drafting"), setOf("Abstract Strategy", "Puzzle")),
        game(148228, "Splendor", 2014, 2..4, setOf(2, 3), 30..30, 1.8, 180, setOf("Engine Building", "Set Collection"), setOf("Card Game", "Economic", "Renaissance")),
        game(266524, "PARKS", 2019, 1..5, setOf(2, 3), 30..60, 2.2, 160, setOf("Set Collection", "Hand Management"), setOf("Animals", "Environmental", "Travel")),
        game(244521, "The Quacks of Quedlinburg", 2018, 2..4, setOf(4), 45..45, 1.9, 110, setOf("Push Your Luck", "Bag Building"), setOf("Fantasy", "Medical")),
        game(30549, "Pandemic", 2008, 2..4, setOf(4), 45..45, 2.4, 140, setOf("Cooperative Game", "Hand Management", "Point to Point Movement"), setOf("Medical")),
        game(161936, "Pandemic Legacy: Season 1", 2015, 2..4, setOf(4), 60..60, 2.8, 7, setOf("Cooperative Game", "Legacy Game", "Hand Management"), setOf("Environmental", "Medical")),
        game(9209, "Ticket to Ride", 2004, 2..5, setOf(4), 30..60, 1.8, 220, setOf("Network and Route Building", "Set Collection", "Hand Management"), setOf("Trains", "Travel")),
        game(13, "CATAN", 1995, 3..4, setOf(4), 60..120, 2.3, 520, setOf("Dice Rolling", "Trading", "Network and Route Building"), setOf("Economic", "Negotiation")),
        game(822, "Carcassonne", 2000, 2..5, setOf(2, 3), 30..45, 1.9, 230, setOf("Tile Placement", "Area Majority / Influence"), setOf("City Building", "Medieval", "Territory Building")),
        game(36218, "Dominion", 2008, 2..4, setOf(3), 30..30, 2.4, 120, setOf("Deck Building", "Hand Management"), setOf("Card Game", "Medieval")),
        game(283155, "Calico", 2020, 1..4, setOf(2), 30..45, 2.2, 260, setOf("Pattern Building", "Tile Placement"), setOf("Animals", "Puzzle")),
        game(204583, "Kingdomino", 2016, 2..4, setOf(2, 4), 15..15, 1.2, 300, setOf("Tile Placement", "Drafting"), setOf("Territory Building", "Medieval")),
        game(70323, "King of Tokyo", 2011, 2..6, setOf(4, 5), 30..30, 1.5, 380, setOf("Dice Rolling", "Player Elimination"), setOf("Fighting", "Science Fiction", "Dice")),
        game(284083, "The Crew: The Quest for Planet Nine", 2019, 2..5, setOf(4), 20..20, 2.0, 80, setOf("Cooperative Game", "Trick-taking", "Communication Limits"), setOf("Card Game", "Space Exploration")),
        game(324856, "The Crew: Mission Deep Sea", 2021, 2..5, setOf(4), 20..20, 2.1, 65, setOf("Cooperative Game", "Trick-taking", "Communication Limits"), setOf("Card Game", "Nautical")),
        game(178900, "Codenames", 2015, 2..8, setOf(6, 8), 15..15, 1.3, 150, setOf("Team-Based Game", "Communication Limits"), party + "Word Game" + "Deduction"),
        game(254640, "Just One", 2018, 3..7, setOf(5, 6, 7), 20..20, 1.1, 200, setOf("Cooperative Game", "Communication Limits"), party + "Word Game"),
        game(39856, "Dixit", 2008, 3..6, setOf(5, 6), 30..30, 1.2, 330, setOf("Storytelling", "Voting"), party + "Card Game"),
        game(128882, "The Resistance: Avalon", 2012, 5..10, setOf(7, 8), 30..30, 1.8, 290, setOf("Hidden Roles", "Voting", "Team-Based Game"), party + "Bluffing" + "Deduction"),
        game(131357, "Coup", 2012, 2..6, setOf(5, 6), 15..15, 1.4, 410, setOf("Hidden Roles", "Player Elimination"), setOf("Bluffing", "Card Game", "Political")),
    )
}
