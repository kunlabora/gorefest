package gorefest.shell.web.components

import gorefest.core.BggId
import gorefest.core.game.Game
import gorefest.shell.web.DEFAULT_TIME_ZONE
import gorefest.shell.web.bgg.codeStep
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
    fun `box art takes http(s) and our own static paths, with quotes and parentheses encoded`() {
        safeImageUrl("https://cf.geekdo-images.com/a.jpg") shouldBe "https://cf.geekdo-images.com/a.jpg"
        safeImageUrl("/static/covers/azul.webp") shouldBe "/static/covers/azul.webp"
        safeImageUrl("javascript:alert(1)") shouldBe null
        safeImageUrl("data:image/png;base64,AAAA") shouldBe null
        safeImageUrl("//evil.example/a.jpg") shouldBe null
        safeImageUrl("https://x.example/a'b\"c(d)e\\f g.jpg") shouldBe "https://x.example/a%27b%22c%28d%29e%5Cf%20g.jpg"
    }

    @Test
    fun `a hostile image URL can't break out of the inline style`() {
        val hostile = art("https://x.example/a');background:url('https://evil.example/t.gif")
        hostile.attr("style").count { it == '\'' } shouldBe 2

        art("javascript:alert(1)").hasClass("gf-art--missing") shouldBe true
    }

    @Test
    fun `the dev BGG button carries its values as valid JSON, whatever is in them`() {
        val html = createHTML().apply { codeStep("/", "a\"b'c", "x\"y", dev = true) }.finalize()
        val vals = Jsoup.parseBodyFragment(html).selectFirst("#dev-bgg button")!!.attr("hx-vals")
        val json = Json.parseToJsonElement(vals).jsonObject

        json["username"]!!.jsonPrimitive.content shouldBe "a\"b'c"
        json["code"]!!.jsonPrimitive.content shouldBe "x\"y"
    }

    @Test
    fun `one zone fallback everywhere`() {
        zoneOrDefault(null).id shouldBe DEFAULT_TIME_ZONE
        zoneOrDefault("Not/AZone").id shouldBe DEFAULT_TIME_ZONE
        zoneOrDefault("UTC").id shouldBe "UTC"
    }
}
