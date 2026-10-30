package gorefest.shell.web.components

import gorefest.contract.Obligation
import gorefest.core.BggId
import gorefest.core.game.Game
import gorefest.shell.web.DEFAULT_TIME_ZONE
import gorefest.shell.web.bgg.codeStep
import gorefest.shell.web.components.initials
import gorefest.shell.web.zoneOrDefault
import io.kotest.matchers.shouldBe
import kotlinx.html.div
import kotlinx.html.stream.createHTML
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.jsoup.Jsoup
import org.junit.jupiter.api.Test

class ComponentsTest {
    private fun game(thumbnail: String?) = Game(BggId(1), "Azul", thumbnail = thumbnail, minPlayers = 2, maxPlayers = 4, minPlaytime = 30, maxPlaytime = 45)

    private fun art(thumbnail: String?) = Jsoup.parseBodyFragment(createHTML().div { art(game(thumbnail)) }).selectFirst(".gf-art")!!

    @Test
    @Obligation("WEB-10")
    fun `box art takes http(s) and our own static paths, with quotes and parentheses encoded`() {
        safeImageUrl("https://cf.geekdo-images.com/a.jpg") shouldBe "https://cf.geekdo-images.com/a.jpg"
        safeImageUrl("/static/covers/azul.webp") shouldBe "/static/covers/azul.webp"
        safeImageUrl("javascript:alert(1)") shouldBe null
        safeImageUrl("data:image/png;base64,AAAA") shouldBe null
        safeImageUrl("//evil.example/a.jpg") shouldBe null
        safeImageUrl("https://x.example/a'b\"c(d)e\\f g.jpg") shouldBe "https://x.example/a%27b%22c(d)e%5Cf%20g.jpg"
        // BGG signs the path, parentheses included: encoding them makes its CDN answer 400.
        safeImageUrl("https://cf.geekdo-images.com/x__small/img/h=/fit-in/200x150/filters:strip_icc()/pic1.jpg") shouldBe
            "https://cf.geekdo-images.com/x__small/img/h=/fit-in/200x150/filters:strip_icc()/pic1.jpg"
    }

    @Test
    @Obligation("WEB-10")
    fun `a hostile image URL can't break out of the inline style`() {
        val hostile = art("https://x.example/a');background:url('https://evil.example/t.gif")
        hostile.attr("style").count { it == '\'' } shouldBe 2

        art("javascript:alert(1)").hasClass("gf-art--missing") shouldBe true
    }

    @Test
    @Obligation("WEB-11")
    fun `the dev BGG button carries its values as valid JSON, whatever is in them`() {
        val html = createHTML().apply { codeStep("/", "a\"b'c", "x\"y", dev = true) }.finalize()
        val vals = Jsoup.parseBodyFragment(html).selectFirst("#dev-bgg button")!!.attr("hx-vals")
        val json = Json.parseToJsonElement(vals).jsonObject

        json["username"]!!.jsonPrimitive.content shouldBe "a\"b'c"
        json["code"]!!.jsonPrimitive.content shouldBe "x\"y"
    }

    @Test
    @Obligation("WEB-12")
    fun `initials are whole characters, and an emoji doesn't count as a word`() {
        initials("Tim Schraepen") shouldBe "TS"
        initials("anna-lena") shouldBe "AL"
        initials("\uD83C\uDFB2 Dice Lord") shouldBe "DL"
        initials("\uD83C\uDFB2Dice") shouldBe "\uD83C\uDFB2"
        initials("\uD83C\uDFB2") shouldBe "\uD83C\uDFB2"
        initials("  ") shouldBe "?"
    }

    @Test
    @Obligation("WEB-13")
    fun `facts show a range, or the typical time when the range is wild`() {
        game(null).facts shouldBe "30–45 min · 2–4"
        game(null).copy(minPlaytime = 5, maxPlaytime = 1000, playingTime = 120).facts shouldBe "~120 min · 2–4"
        game(null).copy(minPlaytime = 60, maxPlaytime = 1200, playingTime = 0).facts shouldBe "~268 min · 2–4"
    }

    @Test
    @Obligation("WEB-14")
    fun `a game's type is one or two telling words from its categories and mechanics`() {
        fun typeOf(name: String) = gorefest.shell.bgg.DevCatalog.games.first { it.name == name }.typeWords
        typeOf("Pandemic") shouldBe listOf("Cooperative")
        typeOf("Wingspan") shouldBe listOf("Engine building", "Dice")
        typeOf("Codenames") shouldBe listOf("Party")
        typeOf("Azul") shouldBe listOf("Abstract", "Tile placing")
        game(null).typeWords shouldBe emptyList()
        game(null).typeText shouldBe ""
        game(null).copy(categories = setOf("Card Game"), mechanics = setOf("Deck, Bag, and Pool Building")).typeText shouldBe "Deck building · Card game"
    }

    @Test
    @Obligation("WEB-15")
    fun `one zone fallback everywhere`() {
        zoneOrDefault(null).id shouldBe DEFAULT_TIME_ZONE
        zoneOrDefault("Not/AZone").id shouldBe DEFAULT_TIME_ZONE
        zoneOrDefault("UTC").id shouldBe "UTC"
    }
}
