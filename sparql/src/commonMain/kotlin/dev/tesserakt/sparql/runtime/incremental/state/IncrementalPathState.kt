@file:Suppress("NOTHING_TO_INLINE")

package dev.tesserakt.sparql.runtime.incremental.state

import dev.tesserakt.sparql.runtime.common.types.Pattern
import dev.tesserakt.sparql.runtime.core.Mapping
import dev.tesserakt.sparql.runtime.core.emptyMapping
import dev.tesserakt.sparql.runtime.core.mappingOf
import dev.tesserakt.sparql.runtime.incremental.collection.mutableJoinCollection
import dev.tesserakt.sparql.runtime.incremental.types.SegmentsList

internal sealed class IncrementalPathState {

    class ZeroOrMoreBinding(
        val start: Pattern.Binding,
        val end: Pattern.Binding
    ) : IncrementalPathState() {

        private val segments = SegmentsList()
        private val arr = mutableJoinCollection(start.name, end.name)

        override val cardinality: Int get() = arr.mappings.size

        override fun insert(segment: SegmentsList.Segment): List<Mapping> {
            val delta = segments.newPathsOnAdding(segment)
                .mapTo(ArrayList()) { mappingOf(start.name to it.start, end.name to it.end) }
            // only accepting all paths that adhere to the constraints above: if one represents a binding, it gets
            //  added to the resulting mappings; else, it gets filtered out if it doesn't match in value
            if (segment.start !in segments.nodes) {
                delta.add(mappingOf(start.name to segment.start, end.name to segment.start))
            }
            if (segment.start != segment.end && segment.end !in segments.nodes) {
                delta.add(mappingOf(start.name to segment.end, end.name to segment.end))
            }
            segments.insert(segment)
            arr.addAll(delta)
            return delta
        }

        override fun join(mappings: List<Mapping>): List<Mapping> {
            return arr.join(mappings)
        }

    }

    class ZeroOrMoreBindingExact(
        val start: Pattern.Binding,
        val end: Pattern.Exact
    ) : IncrementalPathState() {

        private val segments = SegmentsList()
        private val arr = mutableJoinCollection(start.name)

        override val cardinality: Int get() = arr.mappings.size

        override fun insert(segment: SegmentsList.Segment): List<Mapping> {
            val delta = segments.newReachableStartNodesOnAdding(segment)
                .mapTo(ArrayList()) { mappingOf(start.name to it) }
            segments.insert(segment)
            arr.addAll(delta)
            return delta
        }

        override fun join(mappings: List<Mapping>): List<Mapping> {
            return arr.join(mappings)
        }

    }

    class ZeroOrMoreExactBinding(
        val start: Pattern.Exact,
        val end: Pattern.Binding
    ) : IncrementalPathState() {

        private val segments = SegmentsList()
        private val arr = mutableJoinCollection(end.name)

        override val cardinality: Int get() = arr.mappings.size

        override fun insert(segment: SegmentsList.Segment): List<Mapping> {
            val delta = segments.newReachableEndNodesOnAdding(segment)
                .mapTo(ArrayList()) { mappingOf(end.name to it) }
            segments.insert(segment)
            arr.addAll(delta)
            return delta
        }

        override fun join(mappings: List<Mapping>): List<Mapping> {
            return arr.join(mappings)
        }

    }

    class ZeroOrMoreExact(
        val start: Pattern.Exact,
        val end: Pattern.Exact
    ) : IncrementalPathState() {

        private var satisfied = false

        override val cardinality: Int get() = if (satisfied) 1 else 0

        override fun insert(segment: SegmentsList.Segment): List<Mapping> {
            return if (!satisfied) {
                satisfied = true
                listOf(emptyMapping())
            } else {
                emptyList()
            }
        }

        override fun join(mappings: List<Mapping>): List<Mapping> {
            return if (satisfied) mappings else emptyList()
        }

    }

//    class OneOrMore(
//        start: Pattern.Subject,
//        end: Pattern.Object
//    ) : IncrementalPathState(start = start, end = end) {
//
//        override fun insert(segment: SegmentsList.Segment): List<Mapping> {
//            val result = segments.newPathsOnAdding(segment).toMutableList()
//            // only accepting all paths that adhere to the constraints above: if one represents a binding, it gets
//            //  added to the resulting mappings; else, it gets filtered out if it doesn't match in value
//            // FIXME can be done directly in segment list for efficiency
//            if (start is Pattern.Exact) {
//                result.removeAll { it.start != start.term }
//            }
//            if (end is Pattern.Exact) {
//                result.removeAll { it.end != end.term }
//            }
//            val delta = result.map { it.toMapping() }.distinct()
//            segments.insert(segment)
//            return delta
//        }
//
//    }

    abstract val cardinality: Int

    abstract fun insert(segment: SegmentsList.Segment): List<Mapping>

    abstract fun join(mappings: List<Mapping>): List<Mapping>

    companion object {

        fun zeroOrMore(
            start: Pattern.Subject,
            end: Pattern.Object
        ) = when {
            start is Pattern.Exact && end is Pattern.Exact -> ZeroOrMoreExact(start, end)
            start is Pattern.Binding && end is Pattern.Binding -> ZeroOrMoreBinding(start, end)
            start is Pattern.Exact && end is Pattern.Binding -> ZeroOrMoreExactBinding(start, end)
            start is Pattern.Binding && end is Pattern.Exact -> ZeroOrMoreBindingExact(start, end)
            else -> throw IllegalStateException("Unknown subject / pattern combination for `ZeroOrMore` predicate construct: $start -> $end")
        }

        fun oneOrMore(
            start: Pattern.Subject,
            end: Pattern.Object
        ): Nothing = when {
            else -> throw IllegalStateException("Unknown subject / pattern combination for `OneOrMore` predicate construct: $start -> $end")
        }

    }

}

// helper for ZeroOrMore above
private inline operator fun <T> T.plus(collection: Collection<T>): List<T> = ArrayList<T>(collection.size + 1)
    .also { it.add(this); it.addAll(collection) }
