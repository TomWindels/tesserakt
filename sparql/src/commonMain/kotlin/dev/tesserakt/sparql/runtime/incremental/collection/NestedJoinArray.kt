package dev.tesserakt.sparql.runtime.incremental.collection

import dev.tesserakt.sparql.runtime.core.Mapping
import dev.tesserakt.util.compatibleWith
import dev.tesserakt.util.unorderedDrop
import kotlin.jvm.JvmInline

@JvmInline
internal value class NestedJoinArray(
    override val mappings: ArrayList<Mapping> = ArrayList()
): JoinCollection {

    constructor(mappings: Collection<Mapping>): this(ArrayList(mappings))

    override fun join(other: JoinCollection): List<Mapping> {
        return if (other is HashJoinArray) {
            other.join(mappings)
        } else {
            doNestedJoin(a = mappings, b = other.mappings)
        }
    }

    override fun join(other: JoinCollection, ignore: Iterable<Mapping>): List<Mapping> {
        return if (other is HashJoinArray) {
            // the ignore parameter has to be applied to the mappings from this collection, so we have to create a new
            // temporary one
            val adjusted = mappings.unorderedDrop(ignore)
            other.join(mappings = adjusted)
        } else {
            // hardly ideal to drop them here, without indexing this can be a large collection to iterate over
            doNestedJoin(a = mappings.unorderedDrop(ignore), b = other.mappings)
        }
    }

    override fun join(mapping: Mapping): List<Mapping> {
        return buildList(mappings.size) {
            mappings.forEach { contender ->
                if (contender.compatibleWith(mapping)) {
                    add(contender + mapping)
                }
            }
        }
    }

    override fun join(mapping: Mapping, ignore: Iterable<Mapping>): List<Mapping> {
        return buildList(mappings.size) {
            // hardly ideal to drop them here, without indexing this can be a large collection to iterate over
            mappings.unorderedDrop(ignore).forEach { contender ->
                if (contender.compatibleWith(mapping)) {
                    add(contender + mapping)
                }
            }
        }
    }

    override fun join(mappings: List<Mapping>): List<Mapping> {
        return doNestedJoin(a = this.mappings, b = mappings)
    }

    override fun join(mappings: List<Mapping>, ignore: Iterable<Mapping>): List<Mapping> {
        // hardly ideal to drop them here, without indexing this can be a large collection to iterate over
        return doNestedJoin(a = this.mappings.unorderedDrop(ignore), b = mappings)
    }

    override fun add(mapping: Mapping) {
        this.mappings.add(mapping)
    }

    override fun addAll(mappings: Collection<Mapping>) {
        this.mappings.addAll(mappings)
    }

    override fun remove(mapping: Mapping) {
        val i = this.mappings.indexOfLast { it == mapping }
        when (i) {
            -1 -> {
                throw IllegalStateException("$mapping cannot be removed from NestedJoinArray - not found!")
            }
            this.mappings.size - 1 -> {
                this.mappings.removeLast()
            }
            else -> {
                // putting the last element there instead
                this.mappings[i] = this.mappings.removeLast()
            }
        }
    }

    override fun removeAll(mappings: Collection<Mapping>) {
        mappings.forEach(::remove)
    }

    override fun toString() = "NestedJoinArray (cardinality ${mappings.size})"

}

@Suppress("NOTHING_TO_INLINE")
private inline fun doNestedJoin(a: List<Mapping>, b: List<Mapping>) = buildList(a.size + b.size) {
    a.forEach { left ->
        b.forEach { right ->
            if (left.compatibleWith(right)) add(left + right)
        }
    }
}
