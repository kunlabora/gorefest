package gorefest.shell.bgg

import gorefest.contract.Obligation
import gorefest.core.BggId
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import org.junit.jupiter.api.Test

/** Fixtures follow the XML API2 format as documented; they're not captured from the live API. */
class BggXmlTest {
    @Test
    @Obligation("BGG-1")
    fun `a user's profile fields are searchable text`() {
        val user = BggXml.user(BggXml.parse("""
            <user id="123" name="AnnaPlays" termsofuse="https://boardgamegeek.com/xmlapi/termsofuse">
              <firstname value="Anna"/><lastname value=""/><avatarlink value="N/A"/>
              <webaddress value="GORE-ABC234"/><country value="Belgium"/>
            </user>""".trimIndent()))!!

        user.id shouldBe 123
        user.name shouldBe "AnnaPlays"
        user.profileText shouldContain "GORE-ABC234"
    }

    @Test
    @Obligation("BGG-2")
    fun `an unknown user has an empty id`() {
        BggXml.user(BggXml.parse("""<user id="" name="nobody"><firstname value=""/></user>""")).shouldBeNull()
    }

    @Test
    @Obligation("BGG-11", "BGG-13")
    fun `a collection lists owned game ids, or an error for unknown users`() {
        BggXml.collection(BggXml.parse("""
            <items totalitems="2" termsofuse="…" pubdate="…">
              <item objecttype="thing" objectid="13" subtype="boardgame" collid="1"><name sortindex="1">CATAN</name></item>
              <item objecttype="thing" objectid="224517" subtype="boardgame" collid="2"><name sortindex="1">Brass: Birmingham</name></item>
            </items>""".trimIndent())) shouldBe CollectionResult.Ready(setOf(BggId(13), BggId(224517)))

        BggXml.collection(BggXml.parse("<errors><error><message>Invalid username specified</message></error></errors>")) shouldBe
            CollectionResult.UnknownUser
    }

    @Test
    @Obligation("BGG-19")
    fun `a thing carries player counts, the player poll, playtime, weight, links and rank`() {
        val game = BggXml.things(BggXml.parse("""
            <items termsofuse="…">
              <item type="boardgame" id="224517">
                <thumbnail>https://cf.geekdo-images.com/thumb.jpg</thumbnail>
                <image>https://cf.geekdo-images.com/full.jpg</image>
                <name type="primary" sortindex="1" value="Brass: Birmingham"/>
                <name type="alternate" sortindex="1" value="Brass: Birmingham (alt)"/>
                <yearpublished value="2018"/>
                <minplayers value="2"/><maxplayers value="4"/>
                <poll name="suggested_numplayers" title="User Suggested Number of Players" totalvotes="100">
                  <results numplayers="1"><result value="Best" numvotes="0"/><result value="Recommended" numvotes="0"/><result value="Not Recommended" numvotes="0"/></results>
                  <results numplayers="2"><result value="Best" numvotes="10"/><result value="Recommended" numvotes="30"/><result value="Not Recommended" numvotes="20"/></results>
                  <results numplayers="3"><result value="Best" numvotes="50"/><result value="Recommended" numvotes="40"/><result value="Not Recommended" numvotes="2"/></results>
                  <results numplayers="4"><result value="Best" numvotes="80"/><result value="Recommended" numvotes="10"/><result value="Not Recommended" numvotes="1"/></results>
                  <results numplayers="4+"><result value="Best" numvotes="0"/><result value="Recommended" numvotes="0"/><result value="Not Recommended" numvotes="50"/></results>
                </poll>
                <playingtime value="120"/><minplaytime value="60"/><maxplaytime value="120"/>
                <link type="boardgamecategory" id="1021" value="Economic"/>
                <link type="boardgamemechanic" id="2040" value="Hand Management"/>
                <link type="boardgamemechanic" id="2081" value="Network and Route Building"/>
                <statistics page="1"><ratings>
                  <averageweight value="3.8871"/>
                  <ranks>
                    <rank type="subtype" id="1" name="boardgame" friendlyname="Board Game Rank" value="1" bayesaverage="8.4"/>
                    <rank type="family" id="5497" name="strategygames" friendlyname="Strategy Game Rank" value="1" bayesaverage="8.4"/>
                  </ranks>
                </ratings></statistics>
              </item>
            </items>""".trimIndent())).single()

        game.id shouldBe BggId(224517)
        game.name shouldBe "Brass: Birmingham"
        game.year shouldBe 2018
        game.thumbnail shouldBe "https://cf.geekdo-images.com/thumb.jpg"
        (game.minPlayers to game.maxPlayers) shouldBe (2 to 4)
        game.bestPlayers shouldBe setOf(3, 4)
        game.recommendedPlayers shouldBe setOf(2)
        (game.minPlaytime to game.maxPlaytime) shouldBe (60 to 120)
        game.playingTime shouldBe 120
        game.weight shouldBe 3.8871
        game.mechanics shouldBe setOf("Hand Management", "Network and Route Building")
        game.categories shouldBe setOf("Economic")
        game.popularityRank shouldBe 1
    }

    @Test
    @Obligation("BGG-19")
    fun `a campaign game keeps BGG's playing time even when it repeats the max`() {
        val game = BggXml.things(BggXml.parse("""
            <items><item type="boardgame" id="1"><name type="primary" value="Seventh Continent"/><minplayers value="1"/><maxplayers value="4"/>
            <playingtime value="1000"/><minplaytime value="5"/><maxplaytime value="1000"/></item></items>""".trimIndent())).single()

        game.playingTime shouldBe 1000
        game.hasWildPlaytimeRange shouldBe true
        game.typicalMinutes shouldBe 71
    }

    @Test
    @Obligation("BGG-19")
    fun `unranked games have no rank`() {
        BggXml.things(BggXml.parse("""
            <items><item type="boardgame" id="1"><name type="primary" value="Homebrew"/><minplayers value="2"/><maxplayers value="2"/>
            <statistics><ratings><averageweight value="0"/><ranks><rank type="subtype" name="boardgame" value="Not Ranked"/></ranks></ratings></statistics>
            </item></items>""".trimIndent())).single().popularityRank.shouldBeNull()
    }

    @Test
    @Obligation("BGG-20")
    fun `search hits have id, name and year`() {
        BggXml.search(BggXml.parse("""
            <items total="2" termsofuse="…">
              <item type="boardgame" id="266192"><name type="primary" value="Wingspan"/><yearpublished value="2019"/></item>
              <item type="boardgame" id="1"><name type="alternate" value="Wingspan (alt)"/></item>
            </items>""".trimIndent())) shouldContainExactly listOf(SearchHit(BggId(266192), "Wingspan", 2019), SearchHit(BggId(1), "Wingspan (alt)", null))
    }

    @Test
    @Obligation("BGG-28")
    fun `XML with a DOCTYPE is refused`() {
        runCatching { BggXml.parse("""<?xml version="1.0"?><!DOCTYPE x [<!ENTITY e SYSTEM "file:///etc/passwd">]><user id="1" name="&e;"/>""") }
            .isFailure shouldBe true
    }
}
