package gorefest.shell.bgg

import gorefest.core.BggId
import gorefest.core.bgg.Game
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.w3c.dom.Document
import org.w3c.dom.Element
import java.net.URI
import java.net.URLEncoder
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration
import javax.xml.XMLConstants
import javax.xml.parsers.DocumentBuilderFactory

/**
 * The real BGG XML API2. Since late 2025 every request needs `Authorization: Bearer <token>`
 * from an application registered on boardgamegeek.com; unauthenticated requests get a 401.
 */
class XmlBggApi(
    private val token: String,
    private val baseUrl: String = "https://boardgamegeek.com/xmlapi2",
    private val http: HttpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build(),
) : BggApi {

    override suspend fun user(name: String): BggUser? = get("user?name=${name.enc()}")?.let(BggXml::user)

    override suspend fun collection(username: String): CollectionResult {
        val (status, body) = fetch("collection?username=${username.enc()}&own=1&subtype=boardgame&excludesubtype=boardgameexpansion&brief=1")
        return when (status) {
            202 -> CollectionResult.Queued
            200 -> BggXml.collection(BggXml.parse(body))
            else -> throw BggUnavailable("BGG collection answered $status")
        }
    }

    override suspend fun things(ids: Collection<BggId>): List<Game> {
        require(ids.size <= BggApi.THINGS_PER_CALL) { "BGG returns at most ${BggApi.THINGS_PER_CALL} things per call" }
        if (ids.isEmpty()) return emptyList()
        return get("thing?id=${ids.joinToString(",")}&stats=1")?.let(BggXml::things).orEmpty()
    }

    override suspend fun search(query: String): List<SearchHit> =
        get("search?query=${query.enc()}&type=boardgame")?.let(BggXml::search).orEmpty()

    private suspend fun get(path: String): Document? {
        val (status, body) = fetch(path)
        if (status == 404) return null
        if (status != 200) throw BggUnavailable("BGG answered $status for $path")
        return BggXml.parse(body)
    }

    private suspend fun fetch(path: String): Pair<Int, String> = withContext(Dispatchers.IO) {
        val request = HttpRequest.newBuilder(URI("$baseUrl/$path"))
            .header("Authorization", "Bearer $token")
            .header("User-Agent", "gorefest (game-day matcher)")
            .timeout(Duration.ofSeconds(30))
            .GET().build()
        val response = http.send(request, HttpResponse.BodyHandlers.ofString())
        if (response.statusCode() == 429 || response.statusCode() >= 500) throw BggUnavailable("BGG answered ${response.statusCode()}")
        if (response.statusCode() == 401) error("BGG rejected the API token (401). Check BGG_TOKEN.")
        response.statusCode() to response.body()
    }

    private fun String.enc() = URLEncoder.encode(this, Charsets.UTF_8)
}

/** Parsing of BGG's XML, separate from HTTP so it can be tested on fixtures. */
object BggXml {
    fun parse(xml: String): Document = DocumentBuilderFactory.newInstance().apply {
        // BGG is trusted, but never let XML reach out for entities.
        setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true)
        setFeature("http://apache.org/xml/features/disallow-doctype-decl", true)
    }.newDocumentBuilder().parse(xml.byteInputStream())

    /** `<user id="" …>` means there is no such user. */
    fun user(doc: Document): BggUser? {
        val user = doc.documentElement.takeIf { it.tagName == "user" } ?: return null
        val id = user.getAttribute("id").toIntOrNull() ?: return null
        val fields = user.children().map { it.getAttribute("value") }.filter { it.isNotBlank() }
        return BggUser(id, user.getAttribute("name"), fields.joinToString("\n"))
    }

    fun collection(doc: Document): CollectionResult {
        val root = doc.documentElement
        if (root.tagName == "errors") return CollectionResult.UnknownUser
        val ids = root.children("item").mapNotNull { it.getAttribute("objectid").toIntOrNull()?.let(::BggId) }
        return CollectionResult.Ready(ids.toSet())
    }

    fun things(doc: Document): List<Game> = doc.documentElement.children("item").mapNotNull { item ->
        val id = item.getAttribute("id").toIntOrNull() ?: return@mapNotNull null
        val name = item.children("name").firstOrNull { it.getAttribute("type") == "primary" }?.getAttribute("value") ?: return@mapNotNull null
        val (best, recommended) = playerPoll(item)
        val ratings = item.child("statistics")?.child("ratings")
        Game(
            id = BggId(id),
            name = name,
            year = item.value("yearpublished")?.toIntOrNull()?.takeIf { it > 0 },
            thumbnail = item.child("thumbnail")?.textContent?.trim()?.ifBlank { null },
            image = item.child("image")?.textContent?.trim()?.ifBlank { null },
            minPlayers = item.value("minplayers")?.toIntOrNull() ?: 1,
            maxPlayers = item.value("maxplayers")?.toIntOrNull() ?: 1,
            bestPlayers = best,
            recommendedPlayers = recommended,
            minPlaytime = item.value("minplaytime")?.toIntOrNull() ?: item.value("playingtime")?.toIntOrNull() ?: 0,
            maxPlaytime = item.value("maxplaytime")?.toIntOrNull() ?: item.value("playingtime")?.toIntOrNull() ?: 0,
            weight = ratings?.value("averageweight")?.toDoubleOrNull() ?: 0.0,
            mechanics = item.links("boardgamemechanic"),
            categories = item.links("boardgamecategory"),
            rank = ratings?.child("ranks")?.children("rank")
                ?.firstOrNull { it.getAttribute("name") == "boardgame" }?.getAttribute("value")?.toIntOrNull(),
        )
    }

    fun search(doc: Document): List<SearchHit> = doc.documentElement.children("item").mapNotNull { item ->
        val id = item.getAttribute("id").toIntOrNull() ?: return@mapNotNull null
        val name = item.child("name")?.getAttribute("value") ?: return@mapNotNull null
        SearchHit(BggId(id), name, item.value("yearpublished")?.toIntOrNull())
    }

    /**
     * From the "suggested_numplayers" poll: a count is *best* when "Best" got the most votes,
     * *recommended* when Best + Recommended outvote Not Recommended. "4+" style counts are skipped.
     */
    private fun playerPoll(item: Element): Pair<Set<Int>, Set<Int>> {
        val poll = item.children("poll").firstOrNull { it.getAttribute("name") == "suggested_numplayers" } ?: return emptySet<Int>() to emptySet()
        val best = mutableSetOf<Int>()
        val recommended = mutableSetOf<Int>()
        poll.children("results").forEach { results ->
            val count = results.getAttribute("numplayers").toIntOrNull() ?: return@forEach
            val votes = results.children("result").associate { it.getAttribute("value") to (it.getAttribute("numvotes").toIntOrNull() ?: 0) }
            val b = votes["Best"] ?: 0
            val r = votes["Recommended"] ?: 0
            val n = votes["Not Recommended"] ?: 0
            if (b + r + n == 0) return@forEach
            when {
                b >= r && b >= n -> best += count
                b + r > n -> recommended += count
            }
        }
        return best to recommended
    }

    private fun Element.children(tag: String? = null): List<Element> = (0 until childNodes.length)
        .map { childNodes.item(it) }.filterIsInstance<Element>().filter { tag == null || it.tagName == tag }

    private fun Element.child(tag: String) = children(tag).firstOrNull()
    private fun Element.value(tag: String) = child(tag)?.getAttribute("value")
    private fun Element.links(type: String) = children("link").filter { it.getAttribute("type") == type }.map { it.getAttribute("value") }.toSet()
}
