package gorefest.core.eventsourcing

/**
 * The consistency boundary of one decision.
 *
 * An event matches an [Item] when its type is one of [Item.types] and it carries all of [Item.tags].
 * An event matches the query when it matches any item.
 */
data class Query(val items: List<Item>) {
    data class Item(val types: Set<String>, val tags: Set<Tag>)

    fun matches(type: String, tags: Set<Tag>): Boolean =
        items.any { type in it.types && tags.containsAll(it.tags) }

    companion object {
        fun of(vararg items: Item) = Query(items.toList())
    }
}

/** Append only if no event matching [query] was appended after [after] (a global sequence number). */
data class AppendCondition(val query: Query, val after: Long)
