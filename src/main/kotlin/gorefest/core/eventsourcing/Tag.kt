package gorefest.core.eventsourcing

import kotlinx.serialization.Serializable

/** DCB tag, e.g. `gameday:0197…`. Events carry tags; queries select events by type and tags. */
@Serializable
@JvmInline
value class Tag(val value: String) {
    override fun toString() = value
}
