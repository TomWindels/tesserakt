package dev.tesserakt.sparql.runtime.incremental.state

import dev.tesserakt.rdf.types.Quad
import dev.tesserakt.sparql.runtime.common.types.Pattern
import dev.tesserakt.sparql.runtime.core.Mapping
import dev.tesserakt.sparql.runtime.core.emptyMapping
import dev.tesserakt.sparql.runtime.core.mappingOf
import dev.tesserakt.sparql.runtime.core.pattern.matches
import dev.tesserakt.sparql.runtime.incremental.collection.mutableJoinCollection
import dev.tesserakt.sparql.runtime.incremental.types.SegmentsList

internal sealed class IncrementalPathState {

    class ZeroOrMoreStatelessBindings(
        val start: Pattern.Binding,
        val inner: Pattern.StatelessPredicate,
        val end: Pattern.Binding,
    ) : IncrementalPathState() {

        private val segments = SegmentsList()
        private val arr = mutableJoinCollection(start.name, end.name)

        override val cardinality: Int get() = arr.mappings.size

        override fun process(quad: Quad) {
            // TODO(perf): this delta's the segments list twice, can be optimised
            if (!inner.matches(quad.p)) {
                return
            }
            arr.addAll(peek(quad))
            segments.insert(quad.toSegment())
            // two bindings, so adding both ends
            segments.insert(SegmentsList.Segment(quad.s, quad.s))
            segments.insert(SegmentsList.Segment(quad.o, quad.o))
        }

        override fun peek(quad: Quad): List<Mapping> {
            if (!inner.matches(quad.p)) {
                return emptyList()
            }
            // as it's possible for multiple segments to be returned from a single quad insertion, and this in turn
            //  cause some paths to come back in duplicates, we make it instantly distinct
            val result = mutableSetOf<Mapping>()
            segments.newPathsOnAdding(quad.toSegment())
                .mapTo(result) { mappingOf(start.name to it.start, end.name to it.end) }
            // as we're two bindings zero length, the quad's edges can also be null-length paths
            segments.newPathsOnAdding(SegmentsList.Segment(quad.s, quad.s))
                .forEach { result.add(mappingOf(start.name to it.start, end.name to it.end)) }
            segments.newPathsOnAdding(SegmentsList.Segment(quad.o, quad.o))
                .forEach { result.add(mappingOf(start.name to it.start, end.name to it.end)) }
            return result.toList()
        }

        override fun join(mappings: List<Mapping>): List<Mapping> {
            return arr.join(mappings)
        }

        override fun toString() = segments.toString()

    }

    class ZeroOrMoreStatelessBindingExact(
        val start: Pattern.Binding,
        val inner: Pattern.StatelessPredicate,
        val end: Pattern.Exact,
    ) : IncrementalPathState() {

        private val segments = SegmentsList()
        private val arr = mutableJoinCollection(start.name)

        override val cardinality: Int get() = arr.mappings.size

        override fun process(quad: Quad) {
            // TODO(perf): this delta's the segments list twice, can be optimised
            if (!inner.matches(quad.p)) {
                return
            }
            arr.addAll(peek(quad))
            segments.insert(quad.toSegment())
            // only the first represents a binding, so adding the end, but only if it matches exactly
            if (end.matches(quad.o)) {
                segments.insert(SegmentsList.Segment(quad.o, quad.o))
            }
        }

        override fun peek(quad: Quad): List<Mapping> {
            if (!inner.matches(quad.p)) {
                return emptyList()
            }
            // as it's possible for multiple segments to be returned from a single quad insertion, and this in turn
            //  cause some paths to come back in duplicates, we make it instantly distinct
            val result = mutableSetOf<Mapping>()
            segments.newReachableStartNodesOnAdding(quad.toSegment())
                .mapTo(result) { mappingOf(start.name to it) }
            // only adding the end as a zero length binding if it matches the end term
            if (end.matches(quad.o)) {
                segments.newReachableStartNodesOnAdding(SegmentsList.Segment(quad.o, quad.o))
                    .forEach { result.add(mappingOf(start.name to it)) }
            }
            return result.toList()
        }

        override fun join(mappings: List<Mapping>): List<Mapping> {
            return arr.join(mappings)
        }

        override fun toString() = segments.toString()

    }

    class ZeroOrMoreStatelessExactBinding(
        val start: Pattern.Exact,
        val inner: Pattern.StatelessPredicate,
        val end: Pattern.Binding,
    ) : IncrementalPathState() {

        private val segments = SegmentsList()
        private val arr = mutableJoinCollection(end.name)

        override val cardinality: Int get() = arr.mappings.size

        override fun process(quad: Quad) {
            // TODO(perf): this delta's the segments list twice, can be optimised
            if (!inner.matches(quad.p)) {
                return
            }
            arr.addAll(peek(quad))
            segments.insert(quad.toSegment())
            // only the last represents a binding, so adding the end, but only if it matches exactly
            if (start.matches(quad.s)) {
                segments.insert(SegmentsList.Segment(quad.s, quad.s))
            }
        }

        override fun peek(quad: Quad): List<Mapping> {
            if (!inner.matches(quad.p)) {
                return emptyList()
            }
            // as it's possible for multiple segments to be returned from a single quad insertion, and this in turn
            //  cause some paths to come back in duplicates, we make it instantly distinct
            val result = mutableSetOf<Mapping>()
            segments.newReachableEndNodesOnAdding(quad.toSegment())
                .mapTo(result) { mappingOf(end.name to it) }
            // only adding the end as a zero length binding if it matches the end term
            if (start.matches(quad.s)) {
                segments.newReachableEndNodesOnAdding(SegmentsList.Segment(quad.o, quad.o))
                    .forEach { result.add(mappingOf(end.name to it)) }
            }
            return result.toList()
        }

        override fun join(mappings: List<Mapping>): List<Mapping> {
            return arr.join(mappings)
        }

        override fun toString() = segments.toString()

    }

    class ZeroOrMoreExact(
        val start: Pattern.Exact,
        val inner: Pattern.StatelessPredicate,
        val end: Pattern.Exact
    ) : IncrementalPathState() {

        private var satisfied = false

        override val cardinality: Int get() = if (satisfied) 1 else 0

        override fun process(quad: Quad) {
            if (!inner.matches(quad.p)) {
                return
            }
            satisfied = true
        }

        override fun peek(quad: Quad): List<Mapping> {
            // it's expected that a call to `process` will happen soon after,
            //  so not changing it here
            return if (!satisfied && inner.matches(quad.p)) {
                listOf(emptyMapping())
            } else {
                emptyList()
            }
        }

        override fun join(mappings: List<Mapping>): List<Mapping> {
            return if (satisfied) mappings else emptyList()
        }

    }

    class OneOrMoreStatelessBindings(
        val start: Pattern.Binding,
        val inner: Pattern.StatelessPredicate,
        val end: Pattern.Binding,
    ) : IncrementalPathState() {

        private val segments = SegmentsList()
        private val arr = mutableJoinCollection(start.name, end.name)

        override val cardinality: Int get() = arr.mappings.size

        override fun process(quad: Quad) {
            // TODO(perf): this delta's the segments list twice, can be optimised
            if (!inner.matches(quad.p)) {
                return
            }
            arr.addAll(peek(quad))
            segments.insert(quad.toSegment())
        }

        override fun peek(quad: Quad): List<Mapping> {
            if (!inner.matches(quad.p)) {
                return emptyList()
            }
            // as it's possible for multiple segments to be returned from a single quad insertion, and this in turn
            //  cause some paths to come back in duplicates, we make it instantly distinct
            val result = mutableSetOf<Mapping>()
            segments.newPathsOnAdding(quad.toSegment())
                .mapTo(result) { mappingOf(start.name to it.start, end.name to it.end) }
            return result.toList()
        }

        override fun join(mappings: List<Mapping>): List<Mapping> {
            return arr.join(mappings)
        }

        override fun toString() = segments.toString()

    }

    class OneOrMoreStatelessBindingExact(
        val start: Pattern.Binding,
        val inner: Pattern.StatelessPredicate,
        val end: Pattern.Exact,
    ) : IncrementalPathState() {

        private val segments = SegmentsList()
        private val arr = mutableJoinCollection(start.name)

        override val cardinality: Int get() = arr.mappings.size

        override fun process(quad: Quad) {
            // TODO(perf): this delta's the segments list twice, can be optimised
            if (!inner.matches(quad.p)) {
                return
            }
            arr.addAll(peek(quad))
            segments.insert(quad.toSegment())
        }

        override fun peek(quad: Quad): List<Mapping> {
            if (!inner.matches(quad.p)) {
                return emptyList()
            }
            // as it's possible for multiple segments to be returned from a single quad insertion, and this in turn
            //  cause some paths to come back in duplicates, we make it instantly distinct
            val result = mutableSetOf<Mapping>()
            segments.newReachableStartNodesOnAdding(quad.toSegment())
                .mapTo(result) { mappingOf(start.name to it) }
            return result.toList()
        }

        override fun join(mappings: List<Mapping>): List<Mapping> {
            return arr.join(mappings)
        }

        override fun toString() = segments.toString()

    }

    class OneOrMoreStatelessExactBinding(
        val start: Pattern.Exact,
        val inner: Pattern.StatelessPredicate,
        val end: Pattern.Binding,
    ) : IncrementalPathState() {

        private val segments = SegmentsList()
        private val arr = mutableJoinCollection(end.name)

        override val cardinality: Int get() = arr.mappings.size

        override fun process(quad: Quad) {
            // TODO(perf): this delta's the segments list twice, can be optimised
            if (!inner.matches(quad.p)) {
                return
            }
            arr.addAll(peek(quad))
            segments.insert(quad.toSegment())
        }

        override fun peek(quad: Quad): List<Mapping> {
            if (!inner.matches(quad.p)) {
                return emptyList()
            }
            // as it's possible for multiple segments to be returned from a single quad insertion, and this in turn
            //  cause some paths to come back in duplicates, we make it instantly distinct
            val result = mutableSetOf<Mapping>()
            segments.newReachableEndNodesOnAdding(quad.toSegment())
                .mapTo(result) { mappingOf(end.name to it) }
            return result.toList()
        }

        override fun join(mappings: List<Mapping>): List<Mapping> {
            return arr.join(mappings)
        }

        override fun toString() = segments.toString()

    }

    class OneOrMoreExact(
        val start: Pattern.Exact,
        val inner: Pattern.StatelessPredicate,
        val end: Pattern.Exact
    ) : IncrementalPathState() {

        private var satisfied = false

        override val cardinality: Int get() = if (satisfied) 1 else 0

        override fun process(quad: Quad) {
            if (!inner.matches(quad.p)) {
                return
            }
            satisfied = true
        }

        override fun peek(quad: Quad): List<Mapping> {
            // it's expected that a call to `process` will happen soon after,
            //  so not changing it here
            return if (!satisfied && inner.matches(quad.p)) {
                listOf(emptyMapping())
            } else {
                emptyList()
            }
        }

        override fun join(mappings: List<Mapping>): List<Mapping> {
            return if (satisfied) mappings else emptyList()
        }

    }


    abstract val cardinality: Int

    abstract fun process(quad: Quad)

    abstract fun peek(quad: Quad): List<Mapping>

    abstract fun join(mappings: List<Mapping>): List<Mapping>

    companion object {

        fun zeroOrMore(
            start: Pattern.Subject,
            predicate: Pattern.ZeroOrMore,
            end: Pattern.Object
        ): IncrementalPathState {
            return when (val inner = predicate.element) {
                is Pattern.StatelessPredicate -> when {
                    start is Pattern.Binding && end is Pattern.Binding ->
                        ZeroOrMoreStatelessBindings(
                            start = start,
                            inner = inner,
                            end = end,
                        )

                    start is Pattern.Binding && end is Pattern.Exact ->
                        ZeroOrMoreStatelessBindingExact(
                            start = start,
                            inner = inner,
                            end = end,
                        )

                    start is Pattern.Exact && end is Pattern.Binding ->
                        ZeroOrMoreStatelessExactBinding(
                            start = start,
                            inner = inner,
                            end = end,
                        )

                    start is Pattern.Exact && end is Pattern.Exact ->
                        ZeroOrMoreExact(
                            start = start,
                            inner = inner,
                            end = end,
                        )

                    else ->
                        throw IllegalStateException("Internal error: unknown subject / pattern combination for `ZeroOrMore` predicate construct: $start -> $end")
                }

                is Pattern.UnboundSequence -> {
                    // FIXME
                    throw IllegalStateException("Repeating sequence patterns are currently not supported!\nOffending triple pattern: ${Pattern(start, predicate, end)}")
                }

                else -> {
                    throw IllegalStateException("Invalid query: predicate element `${inner}` cannot be used as a repeating (*) element!\nOffending pattern: ${Pattern(start, predicate, end)}")
                }
            }
        }

        fun oneOrMore(
            start: Pattern.Subject,
            predicate: Pattern.OneOrMore,
            end: Pattern.Object
        ): IncrementalPathState {
            return when (val inner = predicate.element) {
                is Pattern.StatelessPredicate -> when {
                    start is Pattern.Binding && end is Pattern.Binding ->
                        OneOrMoreStatelessBindings(
                            start = start,
                            inner = inner,
                            end = end,
                        )

                    start is Pattern.Binding && end is Pattern.Exact ->
                        OneOrMoreStatelessBindingExact(
                            start = start,
                            inner = inner,
                            end = end,
                        )

                    start is Pattern.Exact && end is Pattern.Binding ->
                        OneOrMoreStatelessExactBinding(
                            start = start,
                            inner = inner,
                            end = end,
                        )

                    start is Pattern.Exact && end is Pattern.Exact ->
                        OneOrMoreExact(
                            start = start,
                            inner = inner,
                            end = end,
                        )

                    else ->
                        throw IllegalStateException("Internal error: unknown subject / pattern combination for `OneOrMore` predicate construct: $start -> $end")
                }

                is Pattern.UnboundSequence -> {
                    // FIXME
                    throw IllegalStateException("Repeating sequence patterns are currently not supported!\nOffending triple pattern: ${Pattern(start, predicate, end)}")
                }

                else -> {
                    throw IllegalStateException("Invalid query: predicate element `${inner}` cannot be used as a repeating (+) element!\nOffending pattern: ${Pattern(start, predicate, end)}")
                }
            }
        }

    }

}

// helpers

private fun Quad.toSegment() = SegmentsList.Segment(start = s, end = o)
