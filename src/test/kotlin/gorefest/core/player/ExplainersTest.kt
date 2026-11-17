package gorefest.core.player

import gorefest.contract.Obligation
import gorefest.core.BggId
import gorefest.core.CanExplainChanged
import gorefest.core.Event
import gorefest.core.GamePlayStatusDeclared
import gorefest.core.PlayerErased
import gorefest.core.PlayerId
import gorefest.core.eventsourcing.given
import gorefest.core.taste.PlayerGames
import gorefest.core.taste.Tastes
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import kotlin.uuid.Uuid

class ExplainersTest {
    private val anna = PlayerId(Uuid.random())
    private val bram = PlayerId(Uuid.random())
    private val caro = PlayerId(Uuid.random())
    private val brass = BggId(224517)
    private val azul = BggId(230802)

    @Test
    @Obligation("SH-35")
    fun `a player marks a game they can explain and takes it back`() {
        SetCanExplainDecider.given().whenever(SetCanExplain(anna, brass, true)).thenEmits(CanExplainChanged(anna, brass, true))
        SetCanExplainDecider.given(CanExplainChanged(anna, brass, true)).whenever(SetCanExplain(anna, brass, false))
            .thenEmits(CanExplainChanged(anna, brass, false))
    }

    @Test
    @Obligation("SH-35")
    fun `saying what is already true records nothing`() {
        SetCanExplainDecider.given().whenever(SetCanExplain(anna, brass, false)).thenEmitsNothing()
        SetCanExplainDecider.given(CanExplainChanged(anna, brass, true)).whenever(SetCanExplain(anna, brass, true)).thenEmitsNothing()
        SetCanExplainDecider.given(CanExplainChanged(anna, brass, true), CanExplainChanged(anna, brass, false))
            .whenever(SetCanExplain(anna, brass, false)).thenEmitsNothing()
    }

    @Test
    @Obligation("SH-35")
    fun `the boundary is one player's mark on one game`() {
        val query = SetCanExplainDecider.query(SetCanExplain(anna, brass, true))

        query.matches("CanExplainChanged", CanExplainChanged(anna, brass, true).tags) shouldBe true
        query.matches("CanExplainChanged", CanExplainChanged(bram, brass, true).tags) shouldBe false
        query.matches("CanExplainChanged", CanExplainChanged(anna, azul, true).tags) shouldBe false
    }

    private fun project(vararg events: Event) = events.fold(Explainers.initialState, Explainers::evolve)

    @Test
    @Obligation("SH-35", "SH-36")
    fun `the read model knows who can explain what, among the players asked about`() {
        val explainers = project(
            CanExplainChanged(anna, brass, true),
            CanExplainChanged(caro, brass, true),
            CanExplainChanged(bram, azul, true),
            CanExplainChanged(bram, azul, false),
        )

        explainers.canExplain(anna, brass) shouldBe true
        explainers.canExplain(bram, azul) shouldBe false
        explainers.gamesOf(anna) shouldBe setOf(brass)
        explainers.gamesOf(bram) shouldBe emptySet()
        explainers.of(brass, among = listOf(caro, bram, anna)) shouldBe listOf(caro, anna)
        explainers.of(brass, among = listOf(bram)) shouldBe emptyList()
    }

    @Test
    @Obligation("SH-37")
    fun `an erased player's marks are gone`() {
        val explainers = project(CanExplainChanged(anna, brass, true), CanExplainChanged(bram, brass, true), PlayerErased(anna))

        explainers.gamesOf(anna) shouldBe emptySet()
        explainers.of(brass, among = listOf(anna, bram)) shouldBe listOf(bram)
    }

    private fun tastes(vararg events: Event) = PlayerGames(events.fold(Tastes.initialState, Tastes::evolve).of(anna), emptySet())

    @Test
    @Obligation("TA-33")
    fun `marking a game you can explain marks it played, adds no comparison, and unmarking leaves it played`() {
        val haventPlayed = GamePlayStatusDeclared(anna, brass, played = false)

        tastes(haventPlayed).hasPlayed(brass) shouldBe false
        tastes(haventPlayed, CanExplainChanged(anna, brass, true)).hasPlayed(brass) shouldBe true
        tastes(haventPlayed, CanExplainChanged(anna, brass, true)).isUnknownToThem(brass) shouldBe false
        tastes(CanExplainChanged(anna, brass, true), CanExplainChanged(anna, brass, false)).hasPlayed(brass) shouldBe true
        tastes(CanExplainChanged(anna, brass, true)).history.comparisons shouldBe emptyList()
        // Someone else's mark says nothing about Anna.
        PlayerGames(Tastes.evolve(Tastes.initialState, CanExplainChanged(bram, brass, true)).of(anna), emptySet()).hasPlayed(brass) shouldBe false
    }
}
