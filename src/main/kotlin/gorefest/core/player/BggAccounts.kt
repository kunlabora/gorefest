package gorefest.core.player

import gorefest.core.BggAccountLinked
import gorefest.core.BggAccountLinkedHashed
import gorefest.core.Event
import gorefest.core.PlayerErased
import gorefest.core.PlayerId
import gorefest.core.eventsourcing.Projection

/** Which players linked a BGG account. The username itself isn't here: the log doesn't hold it (the collection sync does). */
class LinkedBggAccounts private constructor(private val players: Set<PlayerId>) {
    fun isLinked(player: PlayerId): Boolean = player in players

    internal fun linked(player: PlayerId) = LinkedBggAccounts(players + player)

    internal fun erased(player: PlayerId) = LinkedBggAccounts(players - player)

    companion object {
        val none = LinkedBggAccounts(emptySet())
    }
}

object BggAccounts : Projection<LinkedBggAccounts> {
    override val initialState = LinkedBggAccounts.none

    override fun evolve(state: LinkedBggAccounts, event: Event) =
        when (event) {
            is BggAccountLinked -> state.linked(event.player)
            is BggAccountLinkedHashed -> state.linked(event.player)
            is PlayerErased -> state.erased(event.player)
            else -> state
        }
}
