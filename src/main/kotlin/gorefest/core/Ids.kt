package gorefest.core

import kotlinx.serialization.Serializable
import kotlin.uuid.Uuid

@Serializable
@JvmInline
value class PlayerId(val value: Uuid) {
    override fun toString() = value.toString()
}

@Serializable
@JvmInline
value class GameDayId(val value: Uuid) {
    override fun toString() = value.toString()

    companion object {
        fun parse(s: String) = GameDayId(Uuid.parse(s))
    }
}

/** BoardGameGeek's id for a game. */
@Serializable
@JvmInline
value class BggId(val value: Int) {
    override fun toString() = value.toString()
}
