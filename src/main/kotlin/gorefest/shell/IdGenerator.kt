package gorefest.shell

import java.util.concurrent.atomic.AtomicLong
import kotlin.uuid.Uuid

/** Source of new ids. The shell mints ids and puts them in commands; the core never generates them. */
fun interface IdGenerator {
    fun next(): Uuid

    companion object {
        val random = IdGenerator { Uuid.random() }

        /** `00000000-0000-0000-0000-000000000001`, `…0002`, … — predictable ids for tests. */
        fun sequential(): IdGenerator {
            val counter = AtomicLong()
            return IdGenerator { Uuid.fromLongs(0, counter.incrementAndGet()) }
        }
    }
}
