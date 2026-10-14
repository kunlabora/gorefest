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

/** A date poll: asking a group which of a few dates suits them, before there is a game day. */
@Serializable
@JvmInline
value class DatePollId(val value: Uuid) {
    override fun toString() = value.toString()

    companion object {
        fun parse(s: String) = DatePollId(Uuid.parse(s))
    }
}

/** A game night (U11): a small evening made from a date poll that asked about games too. */
@Serializable
@JvmInline
value class GameNightId(val value: Uuid) {
    override fun toString() = value.toString()

    companion object {
        fun parse(s: String) = GameNightId(Uuid.parse(s))
    }
}

/** BoardGameGeek's id for a game. */
@Serializable
@JvmInline
value class BggId(val value: Int) {
    override fun toString() = value.toString()
}
