package dev.tesserakt.sparql.runtime.incremental.state

import dev.tesserakt.rdf.types.Quad
import dev.tesserakt.sparql.runtime.common.types.Pattern
import dev.tesserakt.sparql.runtime.core.Mapping
import dev.tesserakt.sparql.runtime.core.emptyMapping
import dev.tesserakt.sparql.runtime.core.mappingOf
import dev.tesserakt.sparql.runtime.core.pattern.matches
import dev.tesserakt.sparql.runtime.incremental.collection.mutableJoinCollection
import dev.tesserakt.sparql.runtime.incremental.delta.Delta
import dev.tesserakt.sparql.runtime.incremental.state.IncrementalTriplePatternState.Companion.createIncrementalPatternState
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

        override fun process(delta: Delta.Data) {
            val quad = delta.value
            // TODO(perf): this delta's the segments list twice, can be optimised
            if (!inner.matches(quad.p)) {
                return
            }
            when (delta) {
                is Delta.DataAddition -> {
                    arr.addAll(peek(delta))
                    segments.insert(quad.toSegment())
                    // two bindings, so adding both ends
                    segments.insert(SegmentsList.Segment(quad.s, quad.s))
                    segments.insert(SegmentsList.Segment(quad.o, quad.o))
                }

                is Delta.DataDeletion -> TODO()
            }
        }

        override fun peek(addition: Delta.DataAddition): List<Mapping> {
            val quad = addition.value
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

        override fun peek(deletion: Delta.DataDeletion): List<Mapping> {
            TODO("Not yet implemented")
        }

        override fun join(mappings: List<Mapping>): List<Mapping> {
            return arr.join(mappings)
        }

        override fun toString() = segments.toString()

    }

    class ZeroOrMoreStatefulBindings(
        val start: Pattern.Binding,
        inner: Pattern.Predicate,
        val end: Pattern.Binding,
    ) : IncrementalPathState() {

        private val segments = SegmentsList()
        private val arr = mutableJoinCollection(start.name, end.name)
        private val inner = Pattern(start, inner, end).createIncrementalPatternState()

        override val cardinality: Int get() = arr.mappings.size

        override fun process(delta: Delta.Data) {
            val quad = delta.value
            when (delta) {
                is Delta.DataAddition -> {
                    arr.addAll(peek(delta))
                    inner.process(Delta.DataAddition(quad))
                    segments.insert(getNewSegments(quad))
                    // two bindings, so adding both ends
                    segments.insert(SegmentsList.Segment(quad.s, quad.s))
                    segments.insert(SegmentsList.Segment(quad.o, quad.o))
                }

                is Delta.DataDeletion -> TODO()
            }
        }

        override fun peek(addition: Delta.DataAddition): List<Mapping> {
            val quad = addition.value
            val new = getNewSegments(quad)
            // as it's possible for multiple segments to be returned from a single quad insertion, and this in turn
            //  cause some paths to come back in duplicates, we make it instantly distinct
            val result = mutableSetOf<Mapping>()
            segments.newPathsOnAdding(new)
                .mapTo(result) { mappingOf(start.name to it.start, end.name to it.end) }
            // as we're two bindings zero length, the quad's edges can also be null-length paths
            segments.newPathsOnAdding(SegmentsList.Segment(quad.s, quad.s))
                .forEach { result.add(mappingOf(start.name to it.start, end.name to it.end)) }
            segments.newPathsOnAdding(SegmentsList.Segment(quad.o, quad.o))
                .forEach { result.add(mappingOf(start.name to it.start, end.name to it.end)) }
            return result.toList()
        }

        override fun peek(deletion: Delta.DataDeletion): List<Mapping> {
            TODO("Not yet implemented")
        }

        override fun join(mappings: List<Mapping>): List<Mapping> {
            return arr.join(mappings)
        }

        override fun toString() = segments.toString()

        private fun getNewSegments(quad: Quad): Set<SegmentsList.Segment> {
            return inner.peek(Delta.DataAddition(quad))
                .mapTo(mutableSetOf()) { SegmentsList.Segment(start = it[start.name]!!, end = it[end.name]!!) }
        }

    }

    class ZeroOrMoreStatelessBindingExact(
        val start: Pattern.Binding,
        val inner: Pattern.StatelessPredicate,
        val end: Pattern.Exact,
    ) : IncrementalPathState() {

        private val segments = SegmentsList()
        private val arr = mutableJoinCollection(start.name)

        override val cardinality: Int get() = arr.mappings.size

        override fun process(delta: Delta.Data) {
            val quad = delta.value
            // TODO(perf): this delta's the segments list twice, can be optimised
            if (!inner.matches(quad.p)) {
                return
            }
            when (delta) {
                is Delta.DataAddition -> {
                    arr.addAll(peek(delta))
                    segments.insert(quad.toSegment())
                    // only the first represents a binding, so adding the end, but only if it matches exactly
                    if (end.matches(quad.o)) {
                        segments.insert(SegmentsList.Segment(quad.o, quad.o))
                    }
                }

                is Delta.DataDeletion -> TODO()
            }
        }

        override fun peek(addition: Delta.DataAddition): List<Mapping> {
            val quad = addition.value
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

        override fun peek(deletion: Delta.DataDeletion): List<Mapping> {
            TODO("Not yet implemented")
        }

        override fun join(mappings: List<Mapping>): List<Mapping> {
            return arr.join(mappings)
        }

        override fun toString() = segments.toString()

    }

    class ZeroOrMoreStatefulBindingExact(
        val start: Pattern.Binding,
        inner: Pattern.Predicate,
        val end: Pattern.Exact,
    ) : IncrementalPathState() {

        private val segments = SegmentsList()
        private val arr = mutableJoinCollection(start.name)

        // "bridge" binding, responsible for keeping the inner predicate's end variable, allowing for more matches that
        //  in turn can produce additional results only obtainable by combining these additional matches; i.e.
        //  A -> B and B -> C should yield A -> C, which is only possible if we don't enforce an exact match B
        private val bridge = createAnonymousBinding()
        private val inner = Pattern(start, inner, bridge).createIncrementalPatternState()

        // all terms that were reached thus far (= new paths where end == exact end), kept track of separately as the
        //  use of the bridge binding makes the path state of the segment list unreliable w/o extra checking
        //  (inner repeating paths may return too many results due to the bridge binding)
        private val reached = mutableSetOf<Quad.Term>()

        override val cardinality: Int get() = arr.mappings.size

        override fun process(delta: Delta.Data) {
            val quad = delta.value
            when (delta) {
                is Delta.DataAddition -> {
                    val peeked = peekNewlyReachable(quad)
                    arr.addAll(peeked.map { mappingOf(start.name to it) })
                    reached.addAll(peeked)
                    inner.process(Delta.DataAddition(quad))
                    segments.insert(getNewSegments(quad))
                    // only the first represents a binding, so adding the end, but only if it matches exactly
                    if (end.matches(quad.o)) {
                        segments.insert(SegmentsList.Segment(quad.o, quad.o))
                    }
                }

                is Delta.DataDeletion -> TODO()
            }
        }

        override fun peek(addition: Delta.DataAddition): List<Mapping> {
            val quad = addition.value
            val result = peekNewlyReachable(quad)
            return result.map { mappingOf(start.name to it) }
        }

        override fun peek(deletion: Delta.DataDeletion): List<Mapping> {
            TODO("Not yet implemented")
        }

        private fun peekNewlyReachable(quad: Quad): Set<Quad.Term> {
            val new = getNewSegments(quad)
            // as it's possible for multiple segments to be returned from a single quad insertion, and this in turn
            //  cause some paths to come back in duplicates, we make it instantly distinct
            val result = mutableSetOf<Quad.Term>()
            segments.newPathsOnAdding(new)
                .forEach {
                    if (it.end != end.term) {
                        return@forEach
                    }
                    if (it.start !in reached) {
                        result.add(it.start)
                    }
                }
            // only adding the end as a zero length binding if it matches the end term
            if (end.matches(quad.o)) {
                segments.newPathsOnAdding(SegmentsList.Segment(quad.o, quad.o))
                    .forEach {
                        if (it.end != end.term) {
                            return@forEach
                        }
                        if (it.start !in reached) {
                            result.add(it.start)
                        }
                    }
            }
            return result
        }

        override fun join(mappings: List<Mapping>): List<Mapping> {
            return arr.join(mappings)
        }

        override fun toString() = segments.toString()

        private fun getNewSegments(quad: Quad): Set<SegmentsList.Segment> {
            return inner.peek(Delta.DataAddition(quad))
                .mapTo(mutableSetOf()) { SegmentsList.Segment(start = it[start.name]!!, end = it[bridge.name]!!) }
        }

    }

    class ZeroOrMoreStatelessExactBinding(
        val start: Pattern.Exact,
        val inner: Pattern.StatelessPredicate,
        val end: Pattern.Binding,
    ) : IncrementalPathState() {

        private val segments = SegmentsList()
        private val arr = mutableJoinCollection(end.name)

        override val cardinality: Int get() = arr.mappings.size

        override fun process(delta: Delta.Data) {
            val quad = delta.value
            // TODO(perf): this delta's the segments list twice, can be optimised
            if (!inner.matches(quad.p)) {
                return
            }
            when (delta) {
                is Delta.DataAddition -> {
                    arr.addAll(peek(delta))
                    segments.insert(quad.toSegment())
                    // only the last represents a binding, so adding the end, but only if it matches exactly
                    if (start.matches(quad.s)) {
                        segments.insert(SegmentsList.Segment(quad.s, quad.s))
                    }
                }

                is Delta.DataDeletion -> TODO()
            }
        }

        override fun peek(addition: Delta.DataAddition): List<Mapping> {
            val quad = addition.value
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
                segments.newReachableEndNodesOnAdding(SegmentsList.Segment(quad.s, quad.s))
                    .forEach { result.add(mappingOf(end.name to it)) }
            }
            return result.toList()
        }

        override fun peek(deletion: Delta.DataDeletion): List<Mapping> {
            TODO("Not yet implemented")
        }

        override fun join(mappings: List<Mapping>): List<Mapping> {
            return arr.join(mappings)
        }

        override fun toString() = segments.toString()

    }

    class ZeroOrMoreStatefulExactBinding(
        val start: Pattern.Exact,
        inner: Pattern.Predicate,
        val end: Pattern.Binding,
    ) : IncrementalPathState() {

        private val segments = SegmentsList()
        private val arr = mutableJoinCollection(end.name)

        // "bridge" binding, responsible for keeping the inner predicate's end variable, allowing for more matches that
        //  in turn can produce additional results only obtainable by combining these additional matches; i.e.
        //  A -> B and B -> C should yield A -> C, which is only possible if we don't enforce an exact match B
        private val bridge = createAnonymousBinding()
        private val inner = Pattern(bridge, inner, end).createIncrementalPatternState()

        // all terms that were reached thus far (= new paths where end == exact end), kept track of separately as the
        //  use of the bridge binding makes the path state of the segment list unreliable w/o extra checking
        //  (inner repeating paths may return too many results due to the bridge binding)
        private val reached = mutableSetOf<Quad.Term>()

        override val cardinality: Int get() = arr.mappings.size

        override fun process(delta: Delta.Data) {
            val quad = delta.value
            when (delta) {
                is Delta.DataAddition -> {
                    val peeked = peekNewlyReachable(quad)
                    arr.addAll(peeked.map { mappingOf(end.name to it) })
                    reached.addAll(peeked)
                    inner.process(Delta.DataAddition(quad))
                    segments.insert(getNewSegments(quad))
                    // only the last represents a binding, so adding the end, but only if it matches exactly
                    if (start.matches(quad.s)) {
                        segments.insert(SegmentsList.Segment(quad.s, quad.s))
                    }
                }

                is Delta.DataDeletion -> TODO()
            }
        }

        override fun peek(addition: Delta.DataAddition): List<Mapping> {
            val quad = addition.value
            val result = peekNewlyReachable(quad)
            return result.map { mappingOf(end.name to it) }
        }

        override fun peek(deletion: Delta.DataDeletion): List<Mapping> {
            TODO("Not yet implemented")
        }

        private fun peekNewlyReachable(quad: Quad): Set<Quad.Term> {
            val new = getNewSegments(quad)
            // as it's possible for multiple segments to be returned from a single quad insertion, and this in turn
            //  cause some paths to come back in duplicates, we make it instantly distinct
            val result = mutableSetOf<Quad.Term>()
            segments.newPathsOnAdding(new)
                .forEach {
                    if (it.start != start.term) {
                        return@forEach
                    }
                    if (it.end !in reached) {
                        result.add(it.end)
                    }
                }
            // only adding the end as a zero length binding if it matches the end term
            if (start.matches(quad.s)) {
                segments.newPathsOnAdding(SegmentsList.Segment(quad.s, quad.s))
                    .forEach {
                        if (it.start != start.term) {
                            return@forEach
                        }
                        if (it.end !in reached) {
                            result.add(it.end)
                        }
                    }
            }
            return result
        }

        override fun join(mappings: List<Mapping>): List<Mapping> {
            return arr.join(mappings)
        }

        override fun toString() = segments.toString()

        private fun getNewSegments(quad: Quad): Set<SegmentsList.Segment> {
            return inner.peek(Delta.DataAddition(quad))
                .mapTo(mutableSetOf()) { SegmentsList.Segment(start = it[bridge.name]!!, end = it[end.name]!!) }
        }

    }

    class ZeroOrMoreStatelessExact(
        val start: Pattern.Exact,
        val inner: Pattern.StatelessPredicate,
        val end: Pattern.Exact
    ) : IncrementalPathState() {

        private var satisfied = false

        override val cardinality: Int get() = if (satisfied) 1 else 0

        override fun process(delta: Delta.Data) {
            val quad = delta.value
            if (!inner.matches(quad.p)) {
                return
            }
            when (delta) {
                is Delta.DataAddition -> {
                    satisfied = true
                }

                is Delta.DataDeletion -> TODO()
            }
        }

        override fun peek(addition: Delta.DataAddition): List<Mapping> {
            val quad = addition.value
            // it's expected that a call to `process` will happen soon after,
            //  so not changing it here
            return if (!satisfied && inner.matches(quad.p)) {
                listOf(emptyMapping())
            } else {
                emptyList()
            }
        }

        override fun peek(deletion: Delta.DataDeletion): List<Mapping> {
            TODO("Not yet implemented")
        }

        override fun join(mappings: List<Mapping>): List<Mapping> {
            return if (satisfied) mappings else emptyList()
        }

    }

    class ZeroOrMoreStatefulExact(
        val start: Pattern.Exact,
        inner: Pattern.Predicate,
        val end: Pattern.Exact
    ) : IncrementalPathState() {

        private var satisfied = false

        // "bridge" bindings, responsible for keeping the inner predicate's connection points variable, allowing for
        //  more matches that in turn can produce additional results only obtainable by combining these additional
        //  matches; i.e.
        //  A -> B and B -> C should yield A -> C, which is only possible if we don't enforce an exact match B
        private val intermediateStart = createAnonymousBinding()
        private val intermediateEnd = createAnonymousBinding()
        private val inner = Pattern(intermediateStart, inner, intermediateEnd).createIncrementalPatternState()
        override val cardinality: Int get() = if (satisfied) 1 else 0

        // these inner results have to be connected as it's possible for multiple quads to form the path
        //  we're looking for
        private val segments = SegmentsList()

        override fun process(delta: Delta.Data) {
            val quad = delta.value
            when (delta) {
                is Delta.DataAddition -> {
                    val peek = inner.peek(delta)
                    val new = peek
                        .mapTo(mutableSetOf()) {
                            SegmentsList.Segment(
                                start = it[intermediateStart.name]!!,
                                end = it[intermediateEnd.name]!!
                            )
                        }
                    if (!satisfied) {
                        satisfied = segments.newPathsOnAdding(new).any { it.start == start.term && it.end == end.term }
                    }
                    inner.process(Delta.DataAddition(quad))
                    segments.insert(new)
                }

                is Delta.DataDeletion -> TODO()
            }
        }

        override fun peek(addition: Delta.DataAddition): List<Mapping> {
            val quad = addition.value
            // it's expected that a call to `process` will happen soon after,
            //  so not changing it here
            if (!satisfied) {
                val peek = inner.peek(Delta.DataAddition(quad))
                val new = peek
                    .mapTo(mutableSetOf()) {
                        SegmentsList.Segment(
                            start = it[intermediateStart.name]!!,
                            end = it[intermediateEnd.name]!!
                        )
                    }
                if (segments.newPathsOnAdding(new).any { it.start == start.term && it.end == end.term }) {
                    return listOf(emptyMapping())
                }
            }
            return emptyList()
        }

        override fun peek(deletion: Delta.DataDeletion): List<Mapping> {
            TODO("Not yet implemented")
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

        override fun process(delta: Delta.Data) {
            val quad = delta.value
            // TODO(perf): this delta's the segments list twice, can be optimised
            if (!inner.matches(quad.p)) {
                return
            }
            when (delta) {
                is Delta.DataAddition -> {
                    arr.addAll(peek(delta))
                    segments.insert(quad.toSegment())
                }

                is Delta.DataDeletion -> TODO()
            }
        }

        override fun peek(addition: Delta.DataAddition): List<Mapping> {
            val quad = addition.value
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

        override fun peek(deletion: Delta.DataDeletion): List<Mapping> {
            TODO("Not yet implemented")
        }

        override fun join(mappings: List<Mapping>): List<Mapping> {
            return arr.join(mappings)
        }

        override fun toString() = segments.toString()

    }

    class OneOrMoreStatefulBindings(
        val start: Pattern.Binding,
        inner: Pattern.Predicate,
        val end: Pattern.Binding,
    ) : IncrementalPathState() {

        private val segments = SegmentsList()
        private val arr = mutableJoinCollection(start.name, end.name)
        private val inner = Pattern(start, inner, end).createIncrementalPatternState()

        override val cardinality: Int get() = arr.mappings.size

        override fun process(delta: Delta.Data) {
            val quad = delta.value
            when (delta) {
                is Delta.DataAddition -> {
                    arr.addAll(peek(delta))
                    inner.process(delta)
                    segments.insert(getNewSegments(quad))
                }

                is Delta.DataDeletion -> TODO()
            }
        }

        override fun peek(addition: Delta.DataAddition): List<Mapping> {
            val quad = addition.value
            val new = getNewSegments(quad)
            // as it's possible for multiple segments to be returned from a single quad insertion, and this in turn
            //  cause some paths to come back in duplicates, we make it instantly distinct
            val result = mutableSetOf<Mapping>()
            segments.newPathsOnAdding(new)
                .mapTo(result) { mappingOf(start.name to it.start, end.name to it.end) }
            return result.toList()
        }

        override fun peek(deletion: Delta.DataDeletion): List<Mapping> {
            TODO("Not yet implemented")
        }

        override fun join(mappings: List<Mapping>): List<Mapping> {
            return arr.join(mappings)
        }

        override fun toString() = segments.toString()

        private fun getNewSegments(quad: Quad): Set<SegmentsList.Segment> {
            return inner.peek(Delta.DataAddition(quad))
                .mapTo(mutableSetOf()) { SegmentsList.Segment(start = it[start.name]!!, end = it[end.name]!!) }
        }

    }

    class OneOrMoreStatelessBindingExact(
        val start: Pattern.Binding,
        val inner: Pattern.StatelessPredicate,
        val end: Pattern.Exact,
    ) : IncrementalPathState() {

        private val segments = SegmentsList()
        private val arr = mutableJoinCollection(start.name)

        override val cardinality: Int get() = arr.mappings.size

        override fun process(delta: Delta.Data) {
            val quad = delta.value
            // TODO(perf): this delta's the segments list twice, can be optimised
            if (!inner.matches(quad.p)) {
                return
            }
            when (delta) {
                is Delta.DataAddition -> {
                    arr.addAll(peek(delta))
                    segments.insert(quad.toSegment())
                }

                is Delta.DataDeletion -> TODO()
            }
        }

        override fun peek(addition: Delta.DataAddition): List<Mapping> {
            val quad = addition.value
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

        override fun peek(deletion: Delta.DataDeletion): List<Mapping> {
            TODO("Not yet implemented")
        }

        override fun join(mappings: List<Mapping>): List<Mapping> {
            return arr.join(mappings)
        }

        override fun toString() = segments.toString()

    }

    class OneOrMoreStatefulBindingExact(
        val start: Pattern.Binding,
        inner: Pattern.Predicate,
        val end: Pattern.Exact,
    ) : IncrementalPathState() {

        private val segments = SegmentsList()
        private val arr = mutableJoinCollection(start.name)

        // "bridge" binding, responsible for keeping the inner predicate's end variable, allowing for more matches that
        //  in turn can produce additional results only obtainable by combining these additional matches; i.e.
        //  A -> B and B -> C should yield A -> C, which is only possible if we don't enforce an exact match B
        private val bridge = createAnonymousBinding()
        private val inner = Pattern(start, inner, bridge).createIncrementalPatternState()

        // all terms that were reached thus far (= new paths where end == exact end), kept track of separately as the
        //  use of the bridge binding makes the path state of the segment list unreliable w/o extra checking
        //  (inner repeating paths may return too many results due to the bridge binding)
        private val reached = mutableSetOf<Quad.Term>()

        override val cardinality: Int get() = arr.mappings.size

        override fun process(delta: Delta.Data) {
            val quad = delta.value
            when (delta) {
                is Delta.DataAddition -> {
                    val peeked = peekNewlyReachable(quad)
                    arr.addAll(peeked.map { mappingOf(start.name to it) })
                    reached.addAll(peeked)
                    inner.process(delta)
                    segments.insert(getNewSegments(quad))
                }

                is Delta.DataDeletion -> TODO()
            }
        }

        override fun peek(addition: Delta.DataAddition): List<Mapping> {
            val quad = addition.value
            val result = peekNewlyReachable(quad)
            return result.map { mappingOf(start.name to it) }
        }

        override fun peek(deletion: Delta.DataDeletion): List<Mapping> {
            TODO("Not yet implemented")
        }

        private fun peekNewlyReachable(quad: Quad): Set<Quad.Term> {
            val new = getNewSegments(quad)
            // as it's possible for multiple segments to be returned from a single quad insertion, and this in turn
            //  cause some paths to come back in duplicates, we make it instantly distinct
            val result = mutableSetOf<Quad.Term>()
            segments.newPathsOnAdding(new)
                .forEach {
                    if (it.end != end.term) {
                        return@forEach
                    }
                    if (it.start !in reached) {
                        result.add(it.start)
                    }
                }
            return result
        }

        override fun join(mappings: List<Mapping>): List<Mapping> {
            return arr.join(mappings)
        }

        override fun toString() = segments.toString()

        private fun getNewSegments(quad: Quad): Set<SegmentsList.Segment> {
            return inner.peek(Delta.DataAddition(quad))
                .mapTo(mutableSetOf()) { SegmentsList.Segment(start = it[start.name]!!, end = it[bridge.name]!!) }
        }

    }

    class OneOrMoreStatelessExactBinding(
        val start: Pattern.Exact,
        val inner: Pattern.StatelessPredicate,
        val end: Pattern.Binding,
    ) : IncrementalPathState() {

        private val segments = SegmentsList()
        private val arr = mutableJoinCollection(end.name)

        override val cardinality: Int get() = arr.mappings.size

        override fun process(delta: Delta.Data) {
            val quad = delta.value
            // TODO(perf): this delta's the segments list twice, can be optimised
            if (!inner.matches(quad.p)) {
                return
            }
            when (delta) {
                is Delta.DataAddition -> {
                    arr.addAll(peek(delta))
                    segments.insert(quad.toSegment())
                }

                is Delta.DataDeletion -> TODO()
            }
        }

        override fun peek(addition: Delta.DataAddition): List<Mapping> {
            val quad = addition.value
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

        override fun peek(deletion: Delta.DataDeletion): List<Mapping> {
            TODO("Not yet implemented")
        }

        override fun join(mappings: List<Mapping>): List<Mapping> {
            return arr.join(mappings)
        }

        override fun toString() = segments.toString()

    }

    class OneOrMoreStatefulExactBinding(
        val start: Pattern.Exact,
        inner: Pattern.Predicate,
        val end: Pattern.Binding,
    ) : IncrementalPathState() {

        private val segments = SegmentsList()
        private val arr = mutableJoinCollection(end.name)

        // "bridge" binding, responsible for keeping the inner predicate's end variable, allowing for more matches that
        //  in turn can produce additional results only obtainable by combining these additional matches; i.e.
        //  A -> B and B -> C should yield A -> C, which is only possible if we don't enforce an exact match B
        private val bridge = createAnonymousBinding()
        private val inner = Pattern(bridge, inner, end).createIncrementalPatternState()

        // all terms that were reached thus far (= new paths where end == exact end), kept track of separately as the
        //  use of the bridge binding makes the path state of the segment list unreliable w/o extra checking
        //  (inner repeating paths may return too many results due to the bridge binding)
        private val reached = mutableSetOf<Quad.Term>()

        override val cardinality: Int get() = arr.mappings.size

        override fun process(delta: Delta.Data) {
            val quad = delta.value
            when (delta) {
                is Delta.DataAddition -> {
                    val peeked = peekNewlyReachable(quad)
                    arr.addAll(peeked.map { mappingOf(end.name to it) })
                    reached.addAll(peeked)
                    inner.process(Delta.DataAddition(quad))
                    segments.insert(getNewSegments(quad))
                }

                is Delta.DataDeletion -> TODO()
            }
        }

        override fun peek(addition: Delta.DataAddition): List<Mapping> {
            val quad = addition.value
            val result = peekNewlyReachable(quad)
            return result.map { mappingOf(end.name to it) }
        }

        override fun peek(deletion: Delta.DataDeletion): List<Mapping> {
            TODO("Not yet implemented")
        }

        private fun peekNewlyReachable(quad: Quad): Set<Quad.Term> {
            val new = getNewSegments(quad)
            // as it's possible for multiple segments to be returned from a single quad insertion, and this in turn
            //  cause some paths to come back in duplicates, we make it instantly distinct
            val result = mutableSetOf<Quad.Term>()
            segments.newPathsOnAdding(new)
                .forEach {
                    if (it.start != start.term) {
                        return@forEach
                    }
                    if (it.end !in reached) {
                        result.add(it.end)
                    }
                }
            return result
        }

        override fun join(mappings: List<Mapping>): List<Mapping> {
            return arr.join(mappings)
        }

        override fun toString() = segments.toString()

        private fun getNewSegments(quad: Quad): Set<SegmentsList.Segment> {
            return inner.peek(Delta.DataAddition(quad))
                .mapTo(mutableSetOf()) { SegmentsList.Segment(start = it[bridge.name]!!, end = it[end.name]!!) }
        }

    }

    class OneOrMoreStatelessExact(
        val start: Pattern.Exact,
        val inner: Pattern.StatelessPredicate,
        val end: Pattern.Exact
    ) : IncrementalPathState() {

        private var satisfied = false

        override val cardinality: Int get() = if (satisfied) 1 else 0

        override fun process(delta: Delta.Data) {
            val quad = delta.value
            if (!inner.matches(quad.p)) {
                return
            }
            when (delta) {
                is Delta.DataAddition -> {
                    satisfied = true
                }

                is Delta.DataDeletion -> TODO()
            }
        }

        override fun peek(addition: Delta.DataAddition): List<Mapping> {
            val quad = addition.value
            // it's expected that a call to `process` will happen soon after,
            //  so not changing it here
            return if (!satisfied && inner.matches(quad.p)) {
                listOf(emptyMapping())
            } else {
                emptyList()
            }
        }

        override fun peek(deletion: Delta.DataDeletion): List<Mapping> {
            TODO("Not yet implemented")
        }

        override fun join(mappings: List<Mapping>): List<Mapping> {
            return if (satisfied) mappings else emptyList()
        }

    }

    class OneOrMoreStatefulExact(
        val start: Pattern.Exact,
        inner: Pattern.Predicate,
        val end: Pattern.Exact
    ) : IncrementalPathState() {

        private var satisfied = false

        // "bridge" bindings, responsible for keeping the inner predicate's connection points variable, allowing for
        //  more matches that in turn can produce additional results only obtainable by combining these additional
        //  matches; i.e.
        //  A -> B and B -> C should yield A -> C, which is only possible if we don't enforce an exact match B
        private val intermediateStart = createAnonymousBinding()
        private val intermediateEnd = createAnonymousBinding()
        private val inner = Pattern(intermediateStart, inner, intermediateEnd).createIncrementalPatternState()
        override val cardinality: Int get() = if (satisfied) 1 else 0

        // these inner results have to be connected as it's possible for multiple quads to form the path
        //  we're looking for
        private val segments = SegmentsList()

        override fun process(delta: Delta.Data) {
            val quad = delta.value
            when (delta) {
                is Delta.DataAddition -> {
                    val peek = inner.peek(delta)
                    val new = peek
                        .mapTo(mutableSetOf()) {
                            SegmentsList.Segment(
                                start = it[intermediateStart.name]!!,
                                end = it[intermediateEnd.name]!!
                            )
                        }
                    if (!satisfied) {
                        satisfied = segments.newPathsOnAdding(new).any { it.start == start.term && it.end == end.term }
                    }
                    inner.process(Delta.DataAddition(quad))
                    segments.insert(new)
                }

                is Delta.DataDeletion -> TODO()
            }
        }

        override fun peek(addition: Delta.DataAddition): List<Mapping> {
            val quad = addition.value
            // it's expected that a call to `process` will happen soon after,
            //  so not changing it here
            if (!satisfied) {
                val peek = inner.peek(Delta.DataAddition(quad))
                val new = peek
                    .mapTo(mutableSetOf()) {
                        SegmentsList.Segment(
                            start = it[intermediateStart.name]!!,
                            end = it[intermediateEnd.name]!!
                        )
                    }
                if (segments.newPathsOnAdding(new).any { it.start == start.term && it.end == end.term }) {
                    return listOf(emptyMapping())
                }
            }
            return emptyList()
        }

        override fun peek(deletion: Delta.DataDeletion): List<Mapping> {
            TODO("Not yet implemented")
        }

        override fun join(mappings: List<Mapping>): List<Mapping> {
            return if (satisfied) mappings else emptyList()
        }

    }


    abstract val cardinality: Int

    abstract fun process(delta: Delta.Data)

    abstract fun peek(addition: Delta.DataAddition): List<Mapping>

    abstract fun peek(deletion: Delta.DataDeletion): List<Mapping>

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
                        ZeroOrMoreStatelessExact(
                            start = start,
                            inner = inner,
                            end = end,
                        )

                    else ->
                        throw IllegalStateException("Internal error: unknown subject / pattern combination for `ZeroOrMore` predicate construct: $start -> $end")
                }

                else -> when {
                    start is Pattern.Binding && end is Pattern.Binding ->
                        ZeroOrMoreStatefulBindings(
                            start = start,
                            inner = inner,
                            end = end,
                        )

                    start is Pattern.Binding && end is Pattern.Exact ->
                        ZeroOrMoreStatefulBindingExact(
                            start = start,
                            inner = inner,
                            end = end,
                        )

                    start is Pattern.Exact && end is Pattern.Binding ->
                        ZeroOrMoreStatefulExactBinding(
                            start = start,
                            inner = inner,
                            end = end,
                        )

                    start is Pattern.Exact && end is Pattern.Exact ->
                        ZeroOrMoreStatefulExact(
                            start = start,
                            inner = inner,
                            end = end,
                        )

                    else ->
                        throw IllegalStateException("Internal error: unknown subject / pattern combination for `ZeroOrMore` predicate construct: $start -> $end")
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
                        OneOrMoreStatelessExact(
                            start = start,
                            inner = inner,
                            end = end,
                        )

                    else ->
                        throw IllegalStateException("Internal error: unknown subject / pattern combination for `OneOrMore` predicate construct: $start -> $end")
                }

                else -> when {
                    start is Pattern.Binding && end is Pattern.Binding ->
                        OneOrMoreStatefulBindings(
                            start = start,
                            inner = inner,
                            end = end,
                        )

                    start is Pattern.Binding && end is Pattern.Exact ->
                        OneOrMoreStatefulBindingExact(
                            start = start,
                            inner = inner,
                            end = end,
                        )

                    start is Pattern.Exact && end is Pattern.Binding ->
                        OneOrMoreStatefulExactBinding(
                            start = start,
                            inner = inner,
                            end = end,
                        )

                    start is Pattern.Exact && end is Pattern.Exact ->
                        OneOrMoreStatefulExact(
                            start = start,
                            inner = inner,
                            end = end,
                        )

                    else ->
                        throw IllegalStateException("Internal error: unknown subject / pattern combination for `OneOrMore` predicate construct: $start -> $end")
                }
            }
        }

    }

}

// helpers

private fun Quad.toSegment() = SegmentsList.Segment(start = s, end = o)
