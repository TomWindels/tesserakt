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

        override fun process(segment: SegmentsList.Segment) {
            val delta = delta(segment)
            segments.insert(segment)
            arr.addAll(delta)
        }

        override fun delta(segment: SegmentsList.Segment): List<Mapping> {
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
            return delta
        }

        override fun join(mappings: List<Mapping>): List<Mapping> {
            return arr.join(mappings)
        }

        override fun toString() = segments.toString()

    }

    class ZeroOrMoreBindingExact(
        val start: Pattern.Binding,
        val end: Pattern.Exact
    ) : IncrementalPathState() {

        private val segments = SegmentsList()
        private val arr = mutableJoinCollection(start.name)

        override val cardinality: Int get() = arr.mappings.size

        override fun process(segment: SegmentsList.Segment) {
            val delta = delta(segment)
            segments.insert(segment)
            arr.addAll(delta)
        }

        override fun delta(segment: SegmentsList.Segment): List<Mapping> {
            return segments.newReachableStartNodesOnAdding(segment)
                .mapTo(ArrayList()) { mappingOf(start.name to it) }
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

        override fun process(segment: SegmentsList.Segment) {
            val delta = delta(segment)
            segments.insert(segment)
            arr.addAll(delta)
        }

        override fun delta(segment: SegmentsList.Segment): List<Mapping> {
            return segments.newReachableEndNodesOnAdding(segment)
                .mapTo(ArrayList()) { mappingOf(end.name to it) }
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

        override fun process(segment: SegmentsList.Segment) {
            satisfied = true
        }

        override fun delta(segment: SegmentsList.Segment): List<Mapping> {
            // it's expected that a call to `process` will happen soon after,
            //  so not changing it here
            return if (!satisfied) {
                listOf(emptyMapping())
            } else {
                emptyList()
            }
        }

        override fun join(mappings: List<Mapping>): List<Mapping> {
            return if (satisfied) mappings else emptyList()
        }

    }

//    class OneOrMoreBinding(
//        val start: Pattern.Binding,
//        val end: Pattern.Binding
//    ) : IncrementalPathState() {
//
//        private val segments = SegmentsList()
//        private val arr = mutableJoinCollection(start.name, end.name)
//
//        override val cardinality: Int get() = arr.mappings.size
//
//        override fun process(segment: SegmentsList.Segment): List<Mapping> {
//            val delta = segments.newPathsOnAdding(segment)
//                .mapTo(ArrayList()) { mappingOf(start.name to it.start, end.name to it.end) }
//            segments.insert(segment)
//            arr.addAll(delta)
//            return delta
//        }
//
//        override fun join(mappings: List<Mapping>): List<Mapping> {
//            return arr.join(mappings)
//        }
//
//    }
//
//    class OneOrMoreBindingExact(
//        val start: Pattern.Binding,
//        val end: Pattern.Exact
//    ) : IncrementalPathState() {
//
//        private val segments = SegmentsList()
//        private val arr = mutableJoinCollection(start.name)
//
//        override val cardinality: Int get() = arr.mappings.size
//
//        override fun process(segment: SegmentsList.Segment): List<Mapping> {
//            val delta = segments.newReachableStartNodesOnAdding(segment)
//                .mapTo(ArrayList()) { mappingOf(start.name to it) }
//            segments.insert(segment)
//            arr.addAll(delta)
//            return delta
//        }
//
//        override fun join(mappings: List<Mapping>): List<Mapping> {
//            return arr.join(mappings)
//        }
//
//    }
//
//    class OneOrMoreExactBinding(
//        val start: Pattern.Exact,
//        val end: Pattern.Binding
//    ) : IncrementalPathState() {
//
//        private val segments = SegmentsList()
//        private val arr = mutableJoinCollection(end.name)
//
//        override val cardinality: Int get() = arr.mappings.size
//
//        override fun process(segment: SegmentsList.Segment): List<Mapping> {
//            val delta = segments.newReachableEndNodesOnAdding(segment)
//                .mapTo(ArrayList()) { mappingOf(end.name to it) }
//            segments.insert(segment)
//            arr.addAll(delta)
//            return delta
//        }
//
//        override fun join(mappings: List<Mapping>): List<Mapping> {
//            return arr.join(mappings)
//        }
//
//    }
//
//    class OneOrMoreExact(
//        val start: Pattern.Exact,
//        val end: Pattern.Exact
//    ) : IncrementalPathState() {
//
//        private var satisfied = false
//
//        override val cardinality: Int get() = if (satisfied) 1 else 0
//
//        override fun process(segment: SegmentsList.Segment): List<Mapping> {
//            return if (!satisfied) {
//                satisfied = true
//                listOf(emptyMapping())
//            } else {
//                emptyList()
//            }
//        }
//
//        override fun join(mappings: List<Mapping>): List<Mapping> {
//            return if (satisfied) mappings else emptyList()
//        }
//
//    }

    abstract val cardinality: Int

    abstract fun process(segment: SegmentsList.Segment)

    abstract fun delta(segment: SegmentsList.Segment): List<Mapping>

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
        ) : Nothing = when {
//            start is Pattern.Exact && end is Pattern.Exact -> OneOrMoreExact(start, end)
//            start is Pattern.Binding && end is Pattern.Binding -> OneOrMoreBinding(start, end)
//            start is Pattern.Exact && end is Pattern.Binding -> OneOrMoreExactBinding(start, end)
//            start is Pattern.Binding && end is Pattern.Exact -> OneOrMoreBindingExact(start, end)
            else -> throw IllegalStateException("Unknown subject / pattern combination for `OneOrMore` predicate construct: $start -> $end")
        }

    }

}
