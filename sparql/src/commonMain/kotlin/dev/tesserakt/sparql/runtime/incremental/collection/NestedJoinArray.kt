package dev.tesserakt.sparql.runtime.incremental.collection

import dev.tesserakt.sparql.runtime.core.Mapping
import dev.tesserakt.util.compatibleWith
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

    override fun join(mapping: Mapping): List<Mapping> {
        return buildList(mappings.size) {
            mappings.forEach { contender ->
                if (contender.compatibleWith(mapping)) {
                    add(contender + mapping)
                }
            }
        }
    }

    override fun join(mappings: List<Mapping>): List<Mapping> {
        return doNestedJoin(a = this.mappings, b = mappings)
    }

    override fun add(mapping: Mapping) {
        this.mappings.add(mapping)
    }

    override fun addAll(mappings: Collection<Mapping>) {
        this.mappings.addAll(mappings)
    }

    override fun remove(mapping: Mapping) {
        this.mappings.remove(mapping)
    }

    override fun removeAll(mappings: Collection<Mapping>) {
        this.mappings.removeAll(mappings)
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

