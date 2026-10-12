package gorefest.core.player

import gorefest.core.BggAccountLinked
import gorefest.core.Event
import gorefest.core.PlayerErased
import gorefest.core.PlayerId
import gorefest.core.eventsourcing.Projection

/** Which BGG username each player linked. */
class LinkedBggAccounts private constructor(private val byPlayer: Map<PlayerId, String>) {
    /** The BGG username [player] linked, or null if they haven't. */
    fun usernameOf(player: PlayerId): String? = byPlayer[player]

    fun isLinked(player: PlayerId): Boolean = player in byPlayer

    internal fun linked(player: PlayerId, username: String) = LinkedBggAccounts(byPlayer + (player to username))

    internal fun erased(player: PlayerId) = LinkedBggAccounts(byPlayer - player)

    companion object {
        val none = LinkedBggAccounts(emptyMap())
    }
}

object BggAccounts : Projection<LinkedBggAccounts> {
    override val initialState = LinkedBggAccounts.none

    override fun evolve(state: LinkedBggAccounts, event: Event) =
        when (event) {
            is BggAccountLinked -> state.linked(event.player, event.username)
            is PlayerErased -> state.erased(event.player)
            else -> state
        }
}
