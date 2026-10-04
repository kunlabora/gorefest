package gorefest.core.eventsourcing

import gorefest.core.Event
import gorefest.core.Rejection
import gorefest.core.type
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf

/**
 * Given/when/then for deciders:
 * ```
 * JoinGameDayDecider
 *     .given(created)
 *     .whenever(JoinGameDay(gameDay, anna))
 *     .thenEmits(PlayerJoined(gameDay, anna))
 * ```
 */
fun <C, S, E : Event> Decider<C, S, E>.given(vararg history: Event) = Given(this, history.toList())

class Given<C, S, E : Event>(private val decider: Decider<C, S, E>, private val history: List<Event>) {
    fun whenever(command: C): Then<E> {
        val query = decider.query(command)
        // Only events inside the decider's boundary may influence its decision, as in production.
        val visible = history.filter { query.matches(it.type, it.tags) }
        val state = visible.fold(decider.initialState, decider::evolve)
        return Then(decider.decide(command, state))
    }
}

class Then<E : Event>(val decision: Decision<E>) {
    fun thenEmits(vararg events: E) {
        decision shouldBe Decision.Accepted(events.toList())
    }

    fun thenEmitsNothing() = thenEmits()

    fun thenRejects(reason: Rejection? = null) {
        decision.shouldBeInstanceOf<Decision.Rejected>()
        if (reason != null) decision.reason shouldBe reason
    }
}
