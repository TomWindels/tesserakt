package dev.tesserakt.sparql.runtime.incremental.state

import dev.tesserakt.rdf.types.Quad
import dev.tesserakt.sparql.runtime.common.types.Pattern
import dev.tesserakt.sparql.runtime.common.util.getTermOrNull
import dev.tesserakt.sparql.runtime.core.Mapping
import dev.tesserakt.sparql.runtime.core.emptyMapping
import dev.tesserakt.sparql.runtime.core.mappingOf
import dev.tesserakt.sparql.runtime.core.pattern.matches
import dev.tesserakt.sparql.runtime.incremental.collection.mutableJoinCollection
import dev.tesserakt.sparql.runtime.incremental.delta.Delta
import dev.tesserakt.sparql.runtime.incremental.state.IncrementalTriplePatternState.Companion.createIncrementalPatternState
import dev.tesserakt.sparql.runtime.incremental.types.SegmentsList
import kotlin.jvm.JvmInline

internal sealed class IncrementalPathState {

    class ZeroOrMoreBinding(
        val start: Pattern.Binding,
        val end: Pattern.Binding,
        val state: PredicateStateHelper<SegmentsList.ZeroLength.SegmentResult>
    ) : IncrementalPathState() {

        private val segments = SegmentsList.ZeroLength()
        private val arr = mutableJoinCollection(start.name, end.name)

        override val cardinality: Int get() = arr.mappings.size

        override fun process(quad: Quad) {
            // TODO(perf): this delta's the segments list twice, can be optimised
            arr.addAll(peek(quad))
            state.peek(quad).forEach { segment -> segments.insert(segment) }
            state.process(quad)
        }

        override fun peek(quad: Quad): List<Mapping> {
            // as it's possible for multiple segments to be returned from a single quad insertion, and this in turn
            //  cause some paths to come back in duplicates, we make it instantly distinct
            return state.peek(quad).flatMapTo(mutableSetOf()) { segment ->
                segments.newPathsOnAdding(segment)
                    .mapTo(ArrayList()) { mappingOf(start.name to it.start, end.name to it.end) }
            }.toList()
        }

        override fun join(mappings: List<Mapping>): List<Mapping> {
            return arr.join(mappings)
        }

        override fun toString() = segments.toString()

    }

    class ZeroOrMoreBindingExact(
        val start: Pattern.Binding,
        val end: Pattern.Exact,
        val state: PredicateStateHelper<SegmentsList.ZeroLength.SegmentResult>
    ) : IncrementalPathState() {

        private val segments = SegmentsList.ZeroLength()
        private val arr = mutableJoinCollection(start.name)

        override val cardinality: Int get() = arr.mappings.size

        override fun process(quad: Quad) {
            // TODO(perf): this delta's the segments list twice, can be optimised
            arr.addAll(peek(quad))
            state.peek(quad).forEach { segments.insert(it) }
            state.process(quad)
        }

        override fun peek(quad: Quad): List<Mapping> {
            return state.peek(quad).flatMap { segment ->
                segments.newReachableStartNodesOnAdding(segment)
                    .mapTo(ArrayList()) { mappingOf(start.name to it) }
            }
        }

        override fun join(mappings: List<Mapping>): List<Mapping> {
            return arr.join(mappings)
        }

    }

    class ZeroOrMoreExactBinding(
        val start: Pattern.Exact,
        val end: Pattern.Binding,
        val state: PredicateStateHelper<SegmentsList.ZeroLength.SegmentResult>
    ) : IncrementalPathState() {

        private val segments = SegmentsList.ZeroLength()
        private val arr = mutableJoinCollection(end.name)

        override val cardinality: Int get() = arr.mappings.size

        override fun process(quad: Quad) {
            // TODO(perf): this delta's the segments list twice, can be optimised
            arr.addAll(peek(quad))
            state.peek(quad).forEach { segments.insert(it) }
            state.process(quad)
        }

        override fun peek(quad: Quad): List<Mapping> {
            return state.peek(quad).flatMap { segment ->
                segments.newReachableEndNodesOnAdding(segment)
                    .mapTo(ArrayList()) { mappingOf(end.name to it) }
            }
        }

        override fun join(mappings: List<Mapping>): List<Mapping> {
            return arr.join(mappings)
        }

    }

    class ZeroOrMoreExact(
        val start: Pattern.Exact,
        val end: Pattern.Exact,
        val predicate: Pattern.ZeroOrMore
    ) : IncrementalPathState() {

        private var satisfied = false

        override val cardinality: Int get() = if (satisfied) 1 else 0

        override fun process(quad: Quad) {
            if (!predicate.element.matches(quad.p)) {
                return
            }
            satisfied = true
        }

        override fun peek(quad: Quad): List<Mapping> {
            // it's expected that a call to `process` will happen soon after,
            //  so not changing it here
            // FIXME is just checking predicates here sufficient?
            return if (!satisfied && predicate.element.matches(quad.p)) {
                listOf(emptyMapping())
            } else {
                emptyList()
            }
        }

        override fun join(mappings: List<Mapping>): List<Mapping> {
            return if (satisfied) mappings else emptyList()
        }

    }

    class OneOrMoreBinding(
        val start: Pattern.Binding,
        val end: Pattern.Binding,
        val state: PredicateStateHelper<SegmentsList.Segment>
    ) : IncrementalPathState() {

        private val segments = SegmentsList.SingleLength()
        private val arr = mutableJoinCollection(start.name, end.name)

        override val cardinality: Int get() = arr.mappings.size

        override fun process(quad: Quad) {
            // TODO(perf): this delta's the segments list twice, can be optimised
            arr.addAll(peek(quad))
            state.peek(quad).forEach { segment -> segments.insert(segment) }
            state.process(quad)
        }

        override fun peek(quad: Quad): List<Mapping> {
            // as it's possible for multiple segments to be returned from a single quad insertion, and this in turn
            //  cause some paths to come back in duplicates, we make it instantly distinct
            return state.peek(quad).flatMapTo(mutableSetOf()) { segment ->
                segments.newPathsOnAdding(segment)
                    .mapTo(ArrayList()) { mappingOf(start.name to it.start, end.name to it.end) }
            }.toList()
        }

        override fun join(mappings: List<Mapping>): List<Mapping> {
            return arr.join(mappings)
        }

        override fun toString() = segments.toString()

    }

    class OneOrMoreBindingExact(
        val start: Pattern.Binding,
        val end: Pattern.Exact,
        val state: PredicateStateHelper<SegmentsList.Segment>
    ) : IncrementalPathState() {

        private val segments = SegmentsList.SingleLength()
        private val arr = mutableJoinCollection(start.name)

        override val cardinality: Int get() = arr.mappings.size

        override fun process(quad: Quad) {
            // TODO(perf): this delta's the segments list twice, can be optimised
            arr.addAll(peek(quad))
            state.peek(quad).forEach { segment -> segments.insert(segment) }
            state.process(quad)
        }

        override fun peek(quad: Quad): List<Mapping> {
            return state.peek(quad).flatMap { segment ->
                segments.newReachableStartNodesOnAdding(segment)
                    .mapTo(ArrayList()) { mappingOf(start.name to it) }
            }
        }

        override fun join(mappings: List<Mapping>): List<Mapping> {
            return arr.join(mappings)
        }

    }

    class OneOrMoreExactBinding(
        val start: Pattern.Exact,
        val end: Pattern.Binding,
        val state: PredicateStateHelper<SegmentsList.Segment>
    ) : IncrementalPathState() {

        private val segments = SegmentsList.SingleLength()
        private val arr = mutableJoinCollection(end.name)

        override val cardinality: Int get() = arr.mappings.size

        override fun process(quad: Quad) {
            // TODO(perf): this delta's the segments list twice, can be optimised
            arr.addAll(peek(quad))
            state.peek(quad).forEach { segment -> segments.insert(segment) }
            state.process(quad)
        }

        override fun peek(quad: Quad): List<Mapping> {
            return state.peek(quad).flatMap { segment ->
                segments.newReachableEndNodesOnAdding(segment)
                    .mapTo(ArrayList()) { mappingOf(end.name to it) }
            }
        }

        override fun join(mappings: List<Mapping>): List<Mapping> {
            return arr.join(mappings)
        }

    }

    class OneOrMoreExact(
        val start: Pattern.Exact,
        val end: Pattern.Exact,
        val predicate: Pattern.OneOrMore
    ) : IncrementalPathState() {

        private var satisfied = false

        override val cardinality: Int get() = if (satisfied) 1 else 0

        override fun process(quad: Quad) {
            if (!predicate.element.matches(quad.p)) {
                return
            }
            satisfied = true
        }

        override fun peek(quad: Quad): List<Mapping> {
            // it's expected that a call to `process` will happen soon after,
            //  so not changing it here
            // FIXME is just testing predicate sufficient?
            return if (!satisfied && predicate.element.matches(quad.p)) {
                listOf(emptyMapping())
            } else {
                emptyList()
            }
        }

        override fun join(mappings: List<Mapping>): List<Mapping> {
            return if (satisfied) mappings else emptyList()
        }

    }

    interface PredicateStateHelper<T : SegmentsList.SegmentHolder> {

        class StatelessSegments(
            override val s: Pattern.Subject,
            private val p: Pattern.Predicate,
            override val o: Pattern.Object
        ) : PredicateStateHelper<SegmentsList.Segment> {
            override fun peek(quad: Quad): List<SegmentsList.Segment> {
                return if (p.matches(quad.p)) {
                    listOf(SegmentsList.Segment(start = quad.s, end = quad.o))
                } else {
                    emptyList()
                }
            }

            override fun process(quad: Quad) {
                // NOP -- stateless
            }
        }

        class StatelessSegmentResults(
            private val start: Pattern.Subject,
            private val p: Pattern.Predicate,
            private val end: Pattern.Object
        ) : PredicateStateHelper<SegmentsList.ZeroLength.SegmentResult> {

            override val s: Pattern.Subject
                get() = start

            override val o: Pattern.Object
                get() = end

            override fun peek(quad: Quad): List<SegmentsList.ZeroLength.SegmentResult> {
                return if (p.matches(quad.p)) {
                    listOf(
                        SegmentsList.ZeroLength.SegmentResult(
                            segment = SegmentsList.Segment(start = quad.s, end = quad.o),
                            isFullMatch = start.matches(quad.s) && end.matches(quad.o)
                        )
                    )
                } else {
                    emptyList()
                }
            }

            override fun process(quad: Quad) {
                // NOP -- stateless
            }

        }

        @JvmInline
        value class StatefulSegments(
            private val state: IncrementalTriplePatternState<*>
        ) : PredicateStateHelper<SegmentsList.Segment> {

            override val s: Pattern.Subject
                get() = state.s

            override val o: Pattern.Object
                get() = state.o

            override fun peek(quad: Quad): List<SegmentsList.Segment> {
                val new = state.peek(Delta.DataAddition(quad))
                val segments = new.map {
                    SegmentsList.Segment(
                        start = state.s.getTermOrNull(it)!!,
                        end = state.o.getTermOrNull(it)!!
                    )
                }
                return segments
            }

            override fun process(quad: Quad) {
                state.process(Delta.DataAddition(quad))
            }
        }

        @JvmInline
        value class StatefulSegmentResults(
            private val state: IncrementalTriplePatternState<*>
        ) : PredicateStateHelper<SegmentsList.ZeroLength.SegmentResult> {

            override val s: Pattern.Subject
                get() = state.s

            override val o: Pattern.Object
                get() = state.o

            override fun peek(quad: Quad): List<SegmentsList.ZeroLength.SegmentResult> {
                val new = state.peek(Delta.DataAddition(quad))
                val segments = new.map {
                    SegmentsList.Segment(
                        start = state.s.getTermOrNull(it)!!,
                        end = state.o.getTermOrNull(it)!!
                    )
                }
                // not sure how to evaluate this, so saying it's a full match
                return segments.map { SegmentsList.ZeroLength.SegmentResult(segment = it, isFullMatch = true) }
            }

            override fun process(quad: Quad) {
                state.process(Delta.DataAddition(quad))
            }
        }

        val s: Pattern.Subject
        val o: Pattern.Object

        fun peek(quad: Quad): List<T>

        /**
         * Inserts this quad if the predicate is not actually stateless
         */
        fun process(quad: Quad)

        companion object {

            fun forSegmentResults(
                start: Pattern.Subject,
                predicate: Pattern.RepeatingPredicate,
                end: Pattern.Object
            ) = when {
                predicate.element is Pattern.StatelessPredicate ->
                    StatelessSegmentResults(start, predicate.element, end)

                else -> StatefulSegmentResults(
                    Pattern(start, predicate.element, end).createIncrementalPatternState()
                )
            }

            fun forSegments(
                start: Pattern.Subject,
                predicate: Pattern.RepeatingPredicate,
                end: Pattern.Object
            ) = when {
                predicate.element is Pattern.StatelessPredicate ->
                    StatelessSegments(start, predicate.element, end)

                else ->
                    StatefulSegments(Pattern(start, predicate.element, end).createIncrementalPatternState())
            }

        }

    }

    abstract val cardinality: Int

    abstract fun process(quad: Quad)

    abstract fun peek(quad: Quad): List<Mapping>

    abstract fun join(mappings: List<Mapping>): List<Mapping>

    companion object {

        fun zeroOrMore(
            start: Pattern.Subject,
            predicate: Pattern.RepeatingPredicate,
            end: Pattern.Object
        ) = when {
            start is Pattern.Exact && end is Pattern.Exact ->
                ZeroOrMoreExact(start = start, end = end, predicate = predicate as Pattern.ZeroOrMore)

            start is Pattern.Binding && end is Pattern.Binding ->
                ZeroOrMoreBinding(
                    start = start,
                    end = end,
                    state = PredicateStateHelper.forSegmentResults(start, predicate, end)
                )

            start is Pattern.Exact && end is Pattern.Binding ->
                ZeroOrMoreExactBinding(
                    start = start,
                    end = end,
                    state = PredicateStateHelper.forSegmentResults(start, predicate, end)
                )

            start is Pattern.Binding && end is Pattern.Exact ->
                ZeroOrMoreBindingExact(
                    start = start,
                    end = end,
                    state = PredicateStateHelper.forSegmentResults(start, predicate, end)
                )

            else -> throw IllegalStateException("Unknown subject / pattern combination for `ZeroOrMore` predicate construct: $start -> $end")
        }

        fun oneOrMore(
            start: Pattern.Subject,
            predicate: Pattern.RepeatingPredicate,
            end: Pattern.Object
        ) = when {
            start is Pattern.Exact && end is Pattern.Exact ->
                OneOrMoreExact(start = start, end = end, predicate = predicate as Pattern.OneOrMore)

            start is Pattern.Binding && end is Pattern.Binding ->
                OneOrMoreBinding(
                    start = start,
                    end = end,
                    state = PredicateStateHelper.forSegments(start, predicate, end)
                )

            start is Pattern.Exact && end is Pattern.Binding ->
                OneOrMoreExactBinding(
                    start = start,
                    end = end,
                    state = PredicateStateHelper.forSegments(start, predicate, end)
                )

            start is Pattern.Binding && end is Pattern.Exact ->
                OneOrMoreBindingExact(
                    start = start,
                    end = end,
                    state = PredicateStateHelper.forSegments(start, predicate, end)
                )

            else -> throw IllegalStateException("Unknown subject / pattern combination for `OneOrMore` predicate construct: $start -> $end")
        }

    }

}
