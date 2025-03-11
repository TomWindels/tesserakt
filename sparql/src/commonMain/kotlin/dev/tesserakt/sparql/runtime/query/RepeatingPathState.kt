package dev.tesserakt.sparql.runtime.query

import dev.tesserakt.rdf.types.Quad
import dev.tesserakt.sparql.ast.TriplePattern
import dev.tesserakt.sparql.ast.matches
import dev.tesserakt.sparql.runtime.collection.MappingArray
import dev.tesserakt.sparql.runtime.evaluation.*
import dev.tesserakt.sparql.runtime.stream.*
import dev.tesserakt.sparql.util.*

internal sealed class RepeatingPathState {

    class ZeroOrMoreStatelessBindings(
        val start: TriplePattern.Binding,
        val inner: TriplePattern.StatelessPredicate,
        val end: TriplePattern.Binding,
    ) : RepeatingPathState() {

        // all terms that have been discovered (count of "zero-length" segments)
        private val terms = Counter<Quad.Term>()
        private val segments = SegmentsList()
        private val arr = MappingArray(start.name, end.name)

        override val cardinality: Cardinality
            get() = arr.cardinality

        override fun process(delta: DataDelta) {
            val quad = delta.value
            // TODO(perf): this delta's the segments list twice, can be optimised
            if (!inner.matches(quad.p)) {
                return
            }
            when (delta) {
                is DataAddition -> {
                    arr.addAll(peek(delta))
                    segments.insert(quad.toSegment())
                    // two bindings, so adding both ends
                    terms.increment(quad.s)
                    terms.increment(quad.o)
                }

                is DataDeletion -> {
                    arr.removeAll(peek(delta))
                    segments.remove(quad.toSegment())
                    // two bindings, so removing both ends
                    terms.decrement(quad.s)
                    terms.decrement(quad.o)
                }
            }
        }

        override fun peek(addition: DataAddition): Stream<Mapping> {
            val quad = addition.value
            if (!inner.matches(quad.p)) {
                return emptyStream()
            }
            // as it's possible for multiple segments to be returned from a single quad insertion, and this in turn
            //  cause some paths to come back in duplicates, we make it instantly distinct
            val result = mutableSetOf<Mapping>()
            segments.newPathsOnAdding(quad.toSegment())
                .mapTo(result) { mappingOf(start.name to it.start, end.name to it.end) }
            // as we're two bindings zero length, the quad's edges can also be null-length paths
            if (quad.s !in terms) {
                result.add(mappingOf(start.name to quad.s, end.name to quad.s))
            }
            if (quad.o !in terms) {
                result.add(mappingOf(start.name to quad.o, end.name to quad.o))
            }
            return result.toStream()
        }

        override fun peek(deletion: DataDeletion): Stream<Mapping> {
            val quad = deletion.value
            if (!inner.matches(quad.p)) {
                return emptyStream()
            }
            // as it's possible for multiple segments to be returned from a single quad insertion, and this in turn
            //  cause some paths to come back in duplicates, we make it instantly distinct
            val result = mutableSetOf<Mapping>()
            segments.removedPathsOnRemoving(quad.toSegment())
                .mapTo(result) { mappingOf(start.name to it.start, end.name to it.end) }
            if (terms[quad.s] == 1) {
                result.add(mappingOf(start.name to quad.s, end.name to quad.s))
            }
            if (terms[quad.o] == 1) {
                result.add(mappingOf(start.name to quad.o, end.name to quad.o))
            }
            return result.toStream()
        }

        override fun join(mappings: OptimisedStream<Mapping>): Stream<Mapping> {
            return arr.join(mappings)
        }

        override fun join(mappings: OptimisedStream<Mapping>, ignore: Iterable<Mapping>): Stream<Mapping> {
            return mappings.transform(maxCardinality = arr.cardinality) { mapping -> arr.iter(mapping).remove(ignore).join(mapping) }
        }

        override fun toString() = segments.toString()

    }

    class ZeroOrMoreStatefulBindings(
        val start: TriplePattern.Binding,
        inner: TriplePattern.Predicate,
        val end: TriplePattern.Binding,
    ) : RepeatingPathState() {

        private val segments = SegmentsList()
        // all terms that have been discovered (count of "zero-length" segments)
        private val terms = Counter<Quad.Term>()
        private val arr = MappingArray(start.name, end.name)
        private val inner = TriplePatternState.from(start, inner, end)

        override val cardinality: Cardinality
            get() = arr.cardinality

        override fun process(delta: DataDelta) {
            val quad = delta.value
            when (delta) {
                is DataAddition -> {
                    arr.addAll(peek(delta))
                    val new = inner.peek(delta)
                        .map { SegmentsList.Segment(start = it[start.name]!!, end = it[end.name]!!) }
                    segments.insert(new)
                    inner.process(delta)
                    terms.increment(quad.s)
                    terms.increment(quad.o)
                }

                is DataDeletion -> {
                    arr.removeAll(peek(delta))
                    val removed = inner.peek(delta)
                        .map { SegmentsList.Segment(start = it[start.name]!!, end = it[end.name]!!) }
                    segments.remove(removed)
                    inner.process(delta)
                    terms.decrement(quad.s)
                    terms.decrement(quad.o)
                }
            }
        }

        override fun peek(addition: DataAddition): Stream<Mapping> {
            val quad = addition.value
            val new = inner.peek(addition)
                .map { SegmentsList.Segment(start = it[start.name]!!, end = it[end.name]!!) }
            // as it's possible for multiple segments to be returned from a single quad insertion, and this in turn
            //  cause some paths to come back in duplicates, we make it instantly distinct
            val result = mutableSetOf<Mapping>()
            segments.newPathsOnAdding(new.toSet())
                .forEach {
                    // ensuring zero lengths aren't included
                    if (it.start != it.end) {
                        result.add(mappingOf(start.name to it.start, end.name to it.end))
                    }
                }
            // as we're two bindings zero length, the quad's edges can also be null-length paths
            if (quad.s !in terms) {
                result.add(mappingOf(start.name to quad.s, end.name to quad.s))
            }
            if (quad.o !in terms) {
                result.add(mappingOf(start.name to quad.o, end.name to quad.o))
            }
            return result.toStream()
        }

        override fun peek(deletion: DataDeletion): Stream<Mapping> {
            val quad = deletion.value
            val removed = inner.peek(deletion)
                .map { SegmentsList.Segment(start = it[start.name]!!, end = it[end.name]!!) }
            // as it's possible for multiple segments to be returned from a single quad insertion, and this in turn
            //  cause some paths to come back in duplicates, we make it instantly distinct
            val result = mutableSetOf<Mapping>()
            segments.removedPathsOnRemoving(removed)
                .forEach {
                    // ensuring zero lengths aren't included
                    if (it.start != it.end) {
                        result.add(mappingOf(start.name to it.start, end.name to it.end))
                    }
                }
            if (terms[quad.s] == 1) {
                result.add(mappingOf(start.name to quad.s, end.name to quad.s))
            }
            if (terms[quad.o] == 1) {
                result.add(mappingOf(start.name to quad.o, end.name to quad.o))
            }
            return result.toStream()
        }

        override fun join(mappings: OptimisedStream<Mapping>): Stream<Mapping> {
            return arr.join(mappings)
        }

        override fun join(mappings: OptimisedStream<Mapping>, ignore: Iterable<Mapping>): Stream<Mapping> {
            return mappings.transform(maxCardinality = arr.cardinality) { mapping -> arr.iter(mapping).remove(ignore).join(mapping) }
        }

        override fun toString() = segments.toString()

    }

    class ZeroOrMoreStatelessBindingExact(
        val start: TriplePattern.Binding,
        val inner: TriplePattern.StatelessPredicate,
        val end: TriplePattern.Exact,
    ) : RepeatingPathState() {

        private val segments = SegmentsList()
        private val arr = MappingArray(start.name)

        override val cardinality: Cardinality
            get() = arr.cardinality

        init {
            // eval(Path(X:term, ZeroOrOnePath(P), Y:var)) = { (Y, yn) | yn = X or {(Y, yn)} in eval(Path(X,P,Y)) }
            arr.add(mappingOf(start.name to end.term))
        }

        override fun process(delta: DataDelta) {
            val quad = delta.value
            // TODO(perf): this delta's the segments list twice, can be optimised
            if (!inner.matches(quad.p)) {
                return
            }
            when (delta) {
                is DataAddition -> {
                    arr.addAll(peek(delta))
                    segments.insert(quad.toSegment())
                }

                is DataDeletion -> {
                    arr.removeAll(peek(delta))
                    segments.remove(quad.toSegment())
                }
            }
        }

        override fun peek(addition: DataAddition): Stream<Mapping> {
            val quad = addition.value
            if (!inner.matches(quad.p)) {
                return emptyStream()
            }
            // as it's possible for multiple segments to be returned from a single quad insertion, and this in turn
            //  cause some paths to come back in duplicates, we make it instantly distinct
            val result = mutableSetOf<Mapping>()
            segments.newPathsOnAdding(quad.toSegment())
                .forEach {
                    if (end.matches(it.end) && !end.matches(it.start)) {
                        result.add(mappingOf(start.name to it.start))
                    }
                }
            return result.toStream()
        }

        override fun peek(deletion: DataDeletion): Stream<Mapping> {
            val quad = deletion.value
            if (!inner.matches(quad.p)) {
                return emptyStream()
            }
            // as it's possible for multiple segments to be returned from a single quad insertion, and this in turn
            //  cause some paths to come back in duplicates, we make it instantly distinct
            val result = mutableSetOf<Mapping>()
            segments.removedPathsOnRemoving(quad.toSegment())
                .forEach {
                    if (end.matches(it.end) && !end.matches(it.start)) {
                        result.add(mappingOf(start.name to it.start))
                    }
                }
            return result.toStream()
        }

        override fun join(mappings: OptimisedStream<Mapping>): Stream<Mapping> {
            return arr.join(mappings)
        }

        override fun join(mappings: OptimisedStream<Mapping>, ignore: Iterable<Mapping>): Stream<Mapping> {
            return mappings.transform(maxCardinality = arr.cardinality) { mapping -> arr.iter(mapping).remove(ignore).join(mapping) }
        }

        override fun toString() = segments.toString()

    }

    class ZeroOrMoreStatefulBindingExact(
        val start: TriplePattern.Binding,
        inner: TriplePattern.Predicate,
        val end: TriplePattern.Exact,
    ) : RepeatingPathState() {

        private val segments = SegmentsList()
        private val arr = MappingArray(start.name)

        // "bridge" binding, responsible for keeping the inner predicate's end variable, allowing for more matches that
        //  in turn can produce additional results only obtainable by combining these additional matches; i.e.
        //  A -> B and B -> C should yield A -> C, which is only possible if we don't enforce an exact match B
        private val bridge = createAnonymousBinding()
        private val inner = TriplePatternState.from(start, inner, bridge)

        override val cardinality: Cardinality
            get() = arr.cardinality

        init {
            // eval(Path(X:term, ZeroOrOnePath(P), Y:var)) = { (Y, yn) | yn = X or {(Y, yn)} in eval(Path(X,P,Y)) }
            arr.add(mappingOf(start.name to end.term))
        }

        override fun process(delta: DataDelta) {
            when (delta) {
                is DataAddition -> {
                    arr.addAll(peek(delta))
                    segments.insert(
                        elements = inner
                            .peek(delta)
                            .map { SegmentsList.Segment(start = it[start.name]!!, end = it[bridge.name]!!) }
                    )
                }

                is DataDeletion -> {
                    arr.removeAll(peek(delta))
                    segments.remove(
                        elements = inner
                            .peek(delta)
                            .map { SegmentsList.Segment(start = it[start.name]!!, end = it[bridge.name]!!) }
                    )
                }
            }
            inner.process(delta)
        }

        override fun peek(addition: DataAddition): Stream<Mapping> {
            val new = inner.peek(addition)
                .mapTo(mutableSetOf()) { SegmentsList.Segment(start = it[start.name]!!, end = it[bridge.name]!!) }
                .ifEmpty { return emptyStream() }
            // as it's possible for multiple segments to be returned from a single quad insertion, and this in turn
            //  cause some paths to come back in duplicates, we make it instantly distinct
            val result = mutableSetOf<Mapping>()
            segments.newPathsOnAdding(new)
                .forEach {
                    if (end.matches(it.end) && !end.matches(it.start)) {
                        result.add(mappingOf(start.name to it.start))
                    }
                }
            return result.toStream()
        }

        override fun peek(deletion: DataDeletion): Stream<Mapping> {
            val removed = inner.peek(deletion)
                .map { SegmentsList.Segment(start = it[start.name]!!, end = it[bridge.name]!!) }
                .ifEmpty { return emptyStream() }
            // as it's possible for multiple segments to be returned from a single quad insertion, and this in turn
            //  cause some paths to come back in duplicates, we make it instantly distinct
            val result = mutableSetOf<Mapping>()
            segments.removedPathsOnRemoving(removed)
                .forEach {
                    // making sure we only include non-zero-length exact matches
                    if (end.matches(it.end) && !end.matches(it.start)) {
                        result.add(mappingOf(start.name to it.start))
                    }
                }
            return result.toStream()
        }

        override fun join(mappings: OptimisedStream<Mapping>): Stream<Mapping> {
            return arr.join(mappings)
        }

        override fun join(mappings: OptimisedStream<Mapping>, ignore: Iterable<Mapping>): Stream<Mapping> {
            return mappings.transform(maxCardinality = arr.cardinality) { mapping -> arr.iter(mapping).remove(ignore).join(mapping) }
        }

        override fun toString() = segments.toString()

    }

    class ZeroOrMoreStatelessExactBinding(
        val start: TriplePattern.Exact,
        val inner: TriplePattern.StatelessPredicate,
        val end: TriplePattern.Binding,
    ) : RepeatingPathState() {

        private val segments = SegmentsList()
        private val arr = MappingArray(end.name)

        override val cardinality: Cardinality
            get() = arr.cardinality

        init {
            // eval(Path(X:term, ZeroOrOnePath(P), Y:var)) = { (Y, yn) | yn = X or {(Y, yn)} in eval(Path(X,P,Y)) }
            arr.add(mappingOf(end.name to start.term))
        }

        override fun process(delta: DataDelta) {
            val quad = delta.value
            // TODO(perf): this delta's the segments list twice, can be optimised
            if (!inner.matches(quad.p)) {
                return
            }
            when (delta) {
                is DataAddition -> {
                    arr.addAll(peek(delta))
                    segments.insert(quad.toSegment())
                }

                is DataDeletion -> {
                    arr.removeAll(peek(delta))
                    segments.remove(quad.toSegment())
                }
            }
        }

        override fun peek(addition: DataAddition): Stream<Mapping> {
            val quad = addition.value
            if (!inner.matches(quad.p)) {
                return emptyStream()
            }
            // as it's possible for multiple segments to be returned from a single quad insertion, and this in turn
            //  cause some paths to come back in duplicates, we make it instantly distinct
            val result = mutableSetOf<Mapping>()
            segments.newPathsOnAdding(quad.toSegment())
                .forEach {
                    if (start.matches(it.start) && !start.matches(it.end)) {
                        result.add(mappingOf(end.name to it.end))
                    }
                }
            return result.toStream()
        }

        override fun peek(deletion: DataDeletion): Stream<Mapping> {
            val quad = deletion.value
            if (!inner.matches(quad.p)) {
                return emptyStream()
            }
            // as it's possible for multiple segments to be returned from a single quad insertion, and this in turn
            //  cause some paths to come back in duplicates, we make it instantly distinct
            val result = mutableSetOf<Mapping>()
            segments.removedPathsOnRemoving(quad.toSegment())
                .forEach {
                    if (start.matches(it.start) && !start.matches(it.end)) {
                        result.add(mappingOf(end.name to it.end))
                    }
                }
            return result.toStream()
        }

        override fun join(mappings: OptimisedStream<Mapping>): Stream<Mapping> {
            return arr.join(mappings)
        }

        override fun join(mappings: OptimisedStream<Mapping>, ignore: Iterable<Mapping>): Stream<Mapping> {
            return mappings.transform(maxCardinality = arr.cardinality) { mapping -> arr.iter(mapping).remove(ignore).join(mapping) }
        }

        override fun toString() = segments.toString()

    }

    class ZeroOrMoreStatefulExactBinding(
        val start: TriplePattern.Exact,
        inner: TriplePattern.Predicate,
        val end: TriplePattern.Binding,
    ) : RepeatingPathState() {

        private val segments = SegmentsList()
        private val arr = MappingArray(end.name)

        // "bridge" binding, responsible for keeping the inner predicate's end variable, allowing for more matches that
        //  in turn can produce additional results only obtainable by combining these additional matches; i.e.
        //  A -> B and B -> C should yield A -> C, which is only possible if we don't enforce an exact match B
        private val bridge = createAnonymousBinding()
        private val inner = TriplePatternState.from(bridge, inner, end)

        override val cardinality: Cardinality
            get() = arr.cardinality

        init {
            // eval(Path(X:term, ZeroOrOnePath(P), Y:var)) = { (Y, yn) | yn = X or {(Y, yn)} in eval(Path(X,P,Y)) }
            arr.add(mappingOf(end.name to start.term))
        }

        override fun process(delta: DataDelta) {
            when (delta) {
                is DataAddition -> {
                    arr.addAll(peek(delta))
                    segments.insert(
                        elements = inner
                            .peek(delta)
                            .map { SegmentsList.Segment(start = it[bridge.name]!!, end = it[end.name]!!) }
                    )
                }

                is DataDeletion -> {
                    arr.removeAll(peek(delta))
                    segments.remove(
                        elements = inner
                            .peek(delta)
                            .map { SegmentsList.Segment(start = it[bridge.name]!!, end = it[end.name]!!) }
                    )
                }
            }
            inner.process(delta)
        }

        override fun peek(addition: DataAddition): Stream<Mapping> {
            val new = inner.peek(addition)
                .mapTo(mutableSetOf()) { SegmentsList.Segment(start = it[bridge.name]!!, end = it[end.name]!!) }
                .ifEmpty { return emptyStream() }
            // as it's possible for multiple segments to be returned from a single quad insertion, and this in turn
            //  cause some paths to come back in duplicates, we make it instantly distinct
            val result = mutableSetOf<Mapping>()
            segments.newPathsOnAdding(new)
                .forEach {
                    if (start.matches(it.start) && !start.matches(it.end)) {
                        result.add(mappingOf(end.name to it.end))
                    }
                }
            return result.toStream()
        }

        override fun peek(deletion: DataDeletion): Stream<Mapping> {
            val removed = inner.peek(deletion)
                .map { SegmentsList.Segment(start = it[bridge.name]!!, end = it[end.name]!!) }
                .ifEmpty { return emptyStream() }
            // as it's possible for multiple segments to be returned from a single quad insertion, and this in turn
            //  cause some paths to come back in duplicates, we make it instantly distinct
            val result = mutableSetOf<Mapping>()
            segments.removedPathsOnRemoving(removed)
                .forEach {
                    if (start.matches(it.start) && !start.matches(it.end)) {
                        result.add(mappingOf(end.name to it.end))
                    }
                }
            return result.toStream()
        }

        override fun join(mappings: OptimisedStream<Mapping>): Stream<Mapping> {
            return arr.join(mappings)
        }

        override fun join(mappings: OptimisedStream<Mapping>, ignore: Iterable<Mapping>): Stream<Mapping> {
            return mappings.transform(maxCardinality = arr.cardinality) { mapping -> arr.iter(mapping).remove(ignore).join(mapping) }
        }

        override fun toString() = segments.toString()

    }

    class ZeroOrMoreStatelessExact(
        val start: TriplePattern.Exact,
        val inner: TriplePattern.StatelessPredicate,
        val end: TriplePattern.Exact
    ) : RepeatingPathState() {

        private var satisfied = start == end

        override val cardinality: Cardinality
            get() = if (satisfied) OneCardinality else ZeroCardinality

        // these inner results have to be connected as it's possible for multiple quads to form the exact path
        //  we're looking for
        private val segments = SegmentsList()

        override fun process(delta: DataDelta) {
            if (start == end) {
                // don't care, always satisfied
                return
            }
            val quad = delta.value
            if (!inner.matches(quad.p)) {
                return
            }
            when (delta) {
                is DataAddition -> {
                    // inserting the segment
                    segments.insert(quad.toSegment())
                    // using the updated segment state to update our satisfied state
                    satisfied = satisfied || segments.paths.any { it.start == start.term && it.end == end.term }
                }

                is DataDeletion -> {
                    // removing the segment
                    segments.remove(quad.toSegment())
                    // using the updated segment state to update our satisfied state
                    // writing the logic like this so the check is short-circuited when possible
                    satisfied = satisfied && segments.paths.any { it.start == start.term && it.end == end.term }
                }
            }
        }

        override fun peek(addition: DataAddition): Stream<Mapping> {
            if (start == end) {
                // don't care, always satisfied
                return emptyStream()
            }
            val quad = addition.value
            if (!inner.matches(quad.p)) {
                return emptyStream()
            }
            // it's expected that a call to `process` will happen soon after,
            //  so not changing it here
            if (!satisfied) {
                val segment = quad.toSegment()
                val new = segments.newPathsOnAdding(segment)
                // checking if any valid path has been reached
                if (new.any { it.start == start.term && it.end == end.term }) {
                    return streamOf(emptyMapping())
                }
            }
            return emptyStream()
        }

        override fun peek(deletion: DataDeletion): Stream<Mapping> {
            if (start == end) {
                // don't care, always satisfied
                return emptyStream()
            }
            val quad = deletion.value
            if (!inner.matches(quad.p)) {
                return emptyStream()
            }
            // it's expected that a call to `process` will happen soon after,
            //  so not changing it here
            if (satisfied) {
                val segment = quad.toSegment()
                val remaining = segments.remainingPathsOnRemoving(segment)
                // checking if any valid path remains
                if (remaining.none { it.start == start.term && it.end == end.term }) {
                    return streamOf(emptyMapping())
                }
            }
            return emptyStream()
        }

        override fun join(mappings: OptimisedStream<Mapping>): Stream<Mapping> {
            return if (satisfied) mappings else emptyStream()
        }

        override fun join(mappings: OptimisedStream<Mapping>, ignore: Iterable<Mapping>): Stream<Mapping> {
            TODO("Not yet implemented")
        }

    }

    class ZeroOrMoreStatefulExact(
        val start: TriplePattern.Exact,
        inner: TriplePattern.Predicate,
        val end: TriplePattern.Exact
    ) : RepeatingPathState() {

        private var satisfied = start == end

        // "bridge" bindings, responsible for keeping the inner predicate's connection points variable, allowing for
        //  more matches that in turn can produce additional results only obtainable by combining these additional
        //  matches; i.e.
        //  A -> B and B -> C should yield A -> C, which is only possible if we don't enforce an exact match B
        private val intermediateStart = createAnonymousBinding()
        private val intermediateEnd = createAnonymousBinding()
        private val inner = TriplePatternState.from(intermediateStart, inner, intermediateEnd)
        override val cardinality: Cardinality
            get() = if (satisfied) OneCardinality else ZeroCardinality

        // these inner results have to be connected as it's possible for multiple quads to form the path
        //  we're looking for
        private val segments = SegmentsList()

        override fun process(delta: DataDelta) {
            if (start == end) {
                // don't care, always satisfied
                return
            }
            when (delta) {
                is DataAddition -> {
                    val peek = inner.peek(delta)
                    val new = peek
                        .map {
                            SegmentsList.Segment(
                                start = it[intermediateStart.name]!!,
                                end = it[intermediateEnd.name]!!
                            )
                        }
                    satisfied = satisfied || segments
                        .newPathsOnAdding(new.toSet())
                        .any { it.start == start.term && it.end == end.term }
                    inner.process(delta)
                    segments.insert(new)
                }

                is DataDeletion -> {
                    val peek = inner.peek(delta)
                    val removed = peek
                        .map {
                            SegmentsList.Segment(
                                start = it[intermediateStart.name]!!,
                                end = it[intermediateEnd.name]!!
                            )
                        }
                    satisfied = satisfied && segments
                        .remainingPathsOnRemoving(removed)
                        .any { it.start == start.term && it.end == end.term }
                    inner.process(delta)
                    segments.remove(removed)
                }
            }
        }

        override fun peek(addition: DataAddition): Stream<Mapping> {
            if (start == end) {
                // don't care, always satisfied
                return emptyStream()
            }
            if (satisfied) {
                return emptyStream()
            }
            val added = inner
                .peek(addition)
                .mapTo(mutableSetOf()) {
                    SegmentsList.Segment(
                        start = it[intermediateStart.name]!!,
                        end = it[intermediateEnd.name]!!
                    )
                }
            if (segments.newPathsOnAdding(added).any { it.start == start.term && it.end == end.term }) {
                return streamOf(emptyMapping())
            }
            return emptyStream()
        }

        override fun peek(deletion: DataDeletion): Stream<Mapping> {
            if (start == end) {
                // don't care, always satisfied
                return emptyStream()
            }
            if (!satisfied) {
                return emptyStream()
            }
            val removed = inner
                .peek(deletion)
                .map {
                    SegmentsList.Segment(
                        start = it[intermediateStart.name]!!,
                        end = it[intermediateEnd.name]!!
                    )
                }
            if (
                segments
                    .remainingPathsOnRemoving(removed)
                    .none { it.start == start.term && it.end == end.term }
            ) {
                return streamOf(emptyMapping())
            }
            return emptyStream()
        }

        override fun join(mappings: OptimisedStream<Mapping>): Stream<Mapping> {
            return if (satisfied) mappings else emptyStream()
        }

        override fun join(mappings: OptimisedStream<Mapping>, ignore: Iterable<Mapping>): Stream<Mapping> {
            TODO("Not yet implemented")
        }

    }

    class OneOrMoreStatelessBindings(
        val start: TriplePattern.Binding,
        val inner: TriplePattern.StatelessPredicate,
        val end: TriplePattern.Binding,
    ) : RepeatingPathState() {

        private val segments = SegmentsList()
        private val arr = MappingArray(start.name, end.name)

        override val cardinality: Cardinality
            get() = arr.cardinality

        override fun process(delta: DataDelta) {
            val quad = delta.value
            // TODO(perf): this delta's the segments list twice, can be optimised
            if (!inner.matches(quad.p)) {
                return
            }
            when (delta) {
                is DataAddition -> {
                    arr.addAll(peek(delta))
                    segments.insert(quad.toSegment())
                }

                is DataDeletion -> TODO()
            }
        }

        override fun peek(addition: DataAddition): Stream<Mapping> {
            val quad = addition.value
            if (!inner.matches(quad.p)) {
                return emptyStream()
            }
            // as it's possible for multiple segments to be returned from a single quad insertion, and this in turn
            //  cause some paths to come back in duplicates, we make it instantly distinct
            val result = mutableSetOf<Mapping>()
            segments.newPathsOnAdding(quad.toSegment())
                .mapTo(result) { mappingOf(start.name to it.start, end.name to it.end) }
            return result.toStream()
        }

        override fun peek(deletion: DataDeletion): Stream<Mapping> {
            TODO("Not yet implemented")
        }

        override fun join(mappings: OptimisedStream<Mapping>): Stream<Mapping> {
            return arr.join(mappings)
        }

        override fun join(mappings: OptimisedStream<Mapping>, ignore: Iterable<Mapping>): Stream<Mapping> {
            return mappings.transform(maxCardinality = arr.cardinality) { mapping -> arr.iter(mapping).remove(ignore).join(mapping) }
        }

        override fun toString() = segments.toString()

    }

    class OneOrMoreStatefulBindings(
        val start: TriplePattern.Binding,
        inner: TriplePattern.Predicate,
        val end: TriplePattern.Binding,
    ) : RepeatingPathState() {

        private val segments = SegmentsList()
        private val arr = MappingArray(start.name, end.name)
        private val inner = TriplePatternState.from(start, inner, end)

        override val cardinality: Cardinality
            get() = arr.cardinality

        override fun process(delta: DataDelta) {
            val quad = delta.value
            when (delta) {
                is DataAddition -> {
                    arr.addAll(peek(delta))
                    inner.process(delta)
                    segments.insert(getNewSegments(quad))
                }

                is DataDeletion -> TODO()
            }
        }

        override fun peek(addition: DataAddition): Stream<Mapping> {
            val quad = addition.value
            val new = getNewSegments(quad)
            // as it's possible for multiple segments to be returned from a single quad insertion, and this in turn
            //  cause some paths to come back in duplicates, we make it instantly distinct
            val result = mutableSetOf<Mapping>()
            segments.newPathsOnAdding(new)
                .mapTo(result) { mappingOf(start.name to it.start, end.name to it.end) }
            return result.toStream()
        }

        override fun peek(deletion: DataDeletion): Stream<Mapping> {
            TODO("Not yet implemented")
        }

        override fun join(mappings: OptimisedStream<Mapping>): Stream<Mapping> {
            return arr.join(mappings)
        }

        override fun join(mappings: OptimisedStream<Mapping>, ignore: Iterable<Mapping>): Stream<Mapping> {
            return mappings.transform(maxCardinality = arr.cardinality) { mapping -> arr.iter(mapping).remove(ignore).join(mapping) }
        }

        override fun toString() = segments.toString()

        private fun getNewSegments(quad: Quad): Set<SegmentsList.Segment> {
            return inner.peek(DataAddition(quad))
                .mapTo(mutableSetOf()) { SegmentsList.Segment(start = it[start.name]!!, end = it[end.name]!!) }
        }

    }

    class OneOrMoreStatelessBindingExact(
        val start: TriplePattern.Binding,
        val inner: TriplePattern.StatelessPredicate,
        val end: TriplePattern.Exact,
    ) : RepeatingPathState() {

        private val segments = SegmentsList()
        private val arr = MappingArray(start.name)

        override val cardinality: Cardinality
            get() = arr.cardinality

        override fun process(delta: DataDelta) {
            val quad = delta.value
            // TODO(perf): this delta's the segments list twice, can be optimised
            if (!inner.matches(quad.p)) {
                return
            }
            when (delta) {
                is DataAddition -> {
                    arr.addAll(peek(delta))
                    segments.insert(quad.toSegment())
                }

                is DataDeletion -> TODO()
            }
        }

        override fun peek(addition: DataAddition): Stream<Mapping> {
            val quad = addition.value
            if (!inner.matches(quad.p)) {
                return emptyStream()
            }
            // as it's possible for multiple segments to be returned from a single quad insertion, and this in turn
            //  cause some paths to come back in duplicates, we make it instantly distinct
            val result = mutableSetOf<Mapping>()
            segments.newReachableStartNodesOnAdding(quad.toSegment())
                .mapTo(result) { mappingOf(start.name to it) }
            return result.toStream()
        }

        override fun peek(deletion: DataDeletion): Stream<Mapping> {
            TODO("Not yet implemented")
        }

        override fun join(mappings: OptimisedStream<Mapping>): Stream<Mapping> {
            return arr.join(mappings)
        }

        override fun join(mappings: OptimisedStream<Mapping>, ignore: Iterable<Mapping>): Stream<Mapping> {
            return mappings.transform(maxCardinality = arr.cardinality) { mapping -> arr.iter(mapping).remove(ignore).join(mapping) }
        }

        override fun toString() = segments.toString()

    }

    class OneOrMoreStatefulBindingExact(
        val start: TriplePattern.Binding,
        inner: TriplePattern.Predicate,
        val end: TriplePattern.Exact,
    ) : RepeatingPathState() {

        private val segments = SegmentsList()
        private val arr = MappingArray(start.name)

        // "bridge" binding, responsible for keeping the inner predicate's end variable, allowing for more matches that
        //  in turn can produce additional results only obtainable by combining these additional matches; i.e.
        //  A -> B and B -> C should yield A -> C, which is only possible if we don't enforce an exact match B
        private val bridge = createAnonymousBinding()
        private val inner = TriplePatternState.from(start, inner, bridge)

        // all terms that were reached thus far (= new paths where end == exact end), kept track of separately as the
        //  use of the bridge binding makes the path state of the segment list unreliable w/o extra checking
        //  (inner repeating paths may return too many results due to the bridge binding)
        private val reached = mutableSetOf<Quad.Term>()

        override val cardinality: Cardinality
            get() = arr.cardinality

        override fun process(delta: DataDelta) {
            val quad = delta.value
            when (delta) {
                is DataAddition -> {
                    val peeked = peekNewlyReachable(quad)
                    arr.addAll(peeked.map { mappingOf(start.name to it) })
                    reached.addAll(peeked)
                    inner.process(delta)
                    segments.insert(getNewSegments(quad))
                }

                is DataDeletion -> TODO()
            }
        }

        override fun peek(addition: DataAddition): Stream<Mapping> {
            val quad = addition.value
            val result = peekNewlyReachable(quad)
            return result.map { mappingOf(start.name to it) }.toStream()
        }

        override fun peek(deletion: DataDeletion): Stream<Mapping> {
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

        override fun join(mappings: OptimisedStream<Mapping>): Stream<Mapping> {
            return arr.join(mappings)
        }

        override fun join(mappings: OptimisedStream<Mapping>, ignore: Iterable<Mapping>): Stream<Mapping> {
            return mappings.transform(maxCardinality = arr.cardinality) { mapping -> arr.iter(mapping).remove(ignore).join(mapping) }
        }

        override fun toString() = segments.toString()

        private fun getNewSegments(quad: Quad): Set<SegmentsList.Segment> {
            return inner.peek(DataAddition(quad))
                .mapTo(mutableSetOf()) { SegmentsList.Segment(start = it[start.name]!!, end = it[bridge.name]!!) }
        }

    }

    class OneOrMoreStatelessExactBinding(
        val start: TriplePattern.Exact,
        val inner: TriplePattern.StatelessPredicate,
        val end: TriplePattern.Binding,
    ) : RepeatingPathState() {

        private val segments = SegmentsList()
        private val arr = MappingArray(end.name)

        override val cardinality: Cardinality
            get() = arr.cardinality

        override fun process(delta: DataDelta) {
            val quad = delta.value
            // TODO(perf): this delta's the segments list twice, can be optimised
            if (!inner.matches(quad.p)) {
                return
            }
            when (delta) {
                is DataAddition -> {
                    arr.addAll(peek(delta))
                    segments.insert(quad.toSegment())
                }

                is DataDeletion -> TODO()
            }
        }

        override fun peek(addition: DataAddition): Stream<Mapping> {
            val quad = addition.value
            if (!inner.matches(quad.p)) {
                return emptyStream()
            }
            // as it's possible for multiple segments to be returned from a single quad insertion, and this in turn
            //  cause some paths to come back in duplicates, we make it instantly distinct
            val result = mutableSetOf<Mapping>()
            segments.newReachableEndNodesOnAdding(quad.toSegment())
                .mapTo(result) { mappingOf(end.name to it) }
            return result.toStream()
        }

        override fun peek(deletion: DataDeletion): Stream<Mapping> {
            TODO("Not yet implemented")
        }

        override fun join(mappings: OptimisedStream<Mapping>): Stream<Mapping> {
            return arr.join(mappings)
        }

        override fun join(mappings: OptimisedStream<Mapping>, ignore: Iterable<Mapping>): Stream<Mapping> {
            return mappings.transform(maxCardinality = arr.cardinality) { mapping -> arr.iter(mapping).remove(ignore).join(mapping) }
        }

        override fun toString() = segments.toString()

    }

    class OneOrMoreStatefulExactBinding(
        val start: TriplePattern.Exact,
        inner: TriplePattern.Predicate,
        val end: TriplePattern.Binding,
    ) : RepeatingPathState() {

        private val segments = SegmentsList()
        private val arr = MappingArray(end.name)

        // "bridge" binding, responsible for keeping the inner predicate's end variable, allowing for more matches that
        //  in turn can produce additional results only obtainable by combining these additional matches; i.e.
        //  A -> B and B -> C should yield A -> C, which is only possible if we don't enforce an exact match B
        private val bridge = createAnonymousBinding()
        private val inner = TriplePatternState.from(bridge, inner, end)

        // all terms that were reached thus far (= new paths where end == exact end), kept track of separately as the
        //  use of the bridge binding makes the path state of the segment list unreliable w/o extra checking
        //  (inner repeating paths may return too many results due to the bridge binding)
        private val reached = mutableSetOf<Quad.Term>()

        override val cardinality: Cardinality
            get() = arr.cardinality

        override fun process(delta: DataDelta) {
            val quad = delta.value
            when (delta) {
                is DataAddition -> {
                    val peeked = peekNewlyReachable(quad)
                    arr.addAll(peeked.map { mappingOf(end.name to it) })
                    reached.addAll(peeked)
                    inner.process(DataAddition(quad))
                    segments.insert(getNewSegments(quad))
                }

                is DataDeletion -> TODO()
            }
        }

        override fun peek(addition: DataAddition): Stream<Mapping> {
            val quad = addition.value
            val result = peekNewlyReachable(quad)
            return result.map { mappingOf(end.name to it) }.toStream()
        }

        override fun peek(deletion: DataDeletion): Stream<Mapping> {
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

        override fun join(mappings: OptimisedStream<Mapping>): Stream<Mapping> {
            return arr.join(mappings)
        }

        override fun join(mappings: OptimisedStream<Mapping>, ignore: Iterable<Mapping>): Stream<Mapping> {
            return mappings.transform(maxCardinality = arr.cardinality) { mapping -> arr.iter(mapping).remove(ignore).join(mapping) }
        }

        override fun toString() = segments.toString()

        private fun getNewSegments(quad: Quad): Set<SegmentsList.Segment> {
            return inner.peek(DataAddition(quad))
                .mapTo(mutableSetOf()) { SegmentsList.Segment(start = it[bridge.name]!!, end = it[end.name]!!) }
        }

    }

    class OneOrMoreStatelessExact(
        val start: TriplePattern.Exact,
        val inner: TriplePattern.StatelessPredicate,
        val end: TriplePattern.Exact
    ) : RepeatingPathState() {

        private var satisfied = false

        override val cardinality: Cardinality
            get() = if (satisfied) OneCardinality else ZeroCardinality

        override fun process(delta: DataDelta) {
            val quad = delta.value
            if (!inner.matches(quad.p)) {
                return
            }
            when (delta) {
                is DataAddition -> {
                    satisfied = true
                }

                is DataDeletion -> TODO()
            }
        }

        override fun peek(addition: DataAddition): Stream<Mapping> {
            val quad = addition.value
            return if (!satisfied && inner.matches(quad.p)) {
                streamOf(emptyMapping())
            } else {
                emptyStream()
            }
        }

        override fun peek(deletion: DataDeletion): Stream<Mapping> {
            TODO("Not yet implemented")
        }

        override fun join(mappings: OptimisedStream<Mapping>): Stream<Mapping> {
            return if (satisfied) mappings else emptyStream()
        }

        override fun join(mappings: OptimisedStream<Mapping>, ignore: Iterable<Mapping>): Stream<Mapping> {
            TODO("Not yet implemented")
        }

    }

    class OneOrMoreStatefulExact(
        val start: TriplePattern.Exact,
        inner: TriplePattern.Predicate,
        val end: TriplePattern.Exact
    ) : RepeatingPathState() {

        private var satisfied = false

        // "bridge" bindings, responsible for keeping the inner predicate's connection points variable, allowing for
        //  more matches that in turn can produce additional results only obtainable by combining these additional
        //  matches; i.e.
        //  A -> B and B -> C should yield A -> C, which is only possible if we don't enforce an exact match B
        private val intermediateStart = createAnonymousBinding()
        private val intermediateEnd = createAnonymousBinding()
        private val inner = TriplePatternState.from(intermediateStart, inner, intermediateEnd)
        override val cardinality: Cardinality
            get() = if (satisfied) OneCardinality else ZeroCardinality

        // these inner results have to be connected as it's possible for multiple quads to form the path
        //  we're looking for
        private val segments = SegmentsList()

        override fun process(delta: DataDelta) {
            val quad = delta.value
            when (delta) {
                is DataAddition -> {
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
                    inner.process(DataAddition(quad))
                    segments.insert(new)
                }

                is DataDeletion -> TODO()
            }
        }

        override fun peek(addition: DataAddition): Stream<Mapping> {
            val quad = addition.value
            if (!satisfied) {
                val peek = inner.peek(DataAddition(quad))
                val new = peek
                    .mapTo(mutableSetOf()) {
                        SegmentsList.Segment(
                            start = it[intermediateStart.name]!!,
                            end = it[intermediateEnd.name]!!
                        )
                    }
                if (segments.newPathsOnAdding(new).any { it.start == start.term && it.end == end.term }) {
                    return streamOf(emptyMapping())
                }
            }
            return emptyStream()
        }

        override fun peek(deletion: DataDeletion): Stream<Mapping> {
            TODO("Not yet implemented")
        }

        override fun join(mappings: OptimisedStream<Mapping>): Stream<Mapping> {
            return if (satisfied) mappings else emptyStream()
        }

        override fun join(mappings: OptimisedStream<Mapping>, ignore: Iterable<Mapping>): Stream<Mapping> {
            TODO("Not yet implemented")
        }

    }


    abstract val cardinality: Cardinality

    abstract fun process(delta: DataDelta)

    abstract fun peek(addition: DataAddition): Stream<Mapping>

    abstract fun peek(deletion: DataDeletion): Stream<Mapping>

    abstract fun join(mappings: OptimisedStream<Mapping>): Stream<Mapping>

    abstract fun join(mappings: OptimisedStream<Mapping>, ignore: Iterable<Mapping>): Stream<Mapping>

    companion object {

        fun zeroOrMore(
            start: TriplePattern.Subject,
            predicate: TriplePattern.ZeroOrMore,
            end: TriplePattern.Object
        ): RepeatingPathState {
            return when (val inner = predicate.element) {
                is TriplePattern.StatelessPredicate -> when {
                    start is TriplePattern.Binding && end is TriplePattern.Binding ->
                        ZeroOrMoreStatelessBindings(
                            start = start,
                            inner = inner,
                            end = end,
                        )

                    start is TriplePattern.Binding && end is TriplePattern.Exact ->
                        ZeroOrMoreStatelessBindingExact(
                            start = start,
                            inner = inner,
                            end = end,
                        )

                    start is TriplePattern.Exact && end is TriplePattern.Binding ->
                        ZeroOrMoreStatelessExactBinding(
                            start = start,
                            inner = inner,
                            end = end,
                        )

                    start is TriplePattern.Exact && end is TriplePattern.Exact ->
                        ZeroOrMoreStatelessExact(
                            start = start,
                            inner = inner,
                            end = end,
                        )

                    else ->
                        throw IllegalStateException("Internal error: unknown subject / pattern combination for `ZeroOrMore` predicate construct: $start -> $end")
                }

                else -> when {
                    start is TriplePattern.Binding && end is TriplePattern.Binding ->
                        ZeroOrMoreStatefulBindings(
                            start = start,
                            inner = inner,
                            end = end,
                        )

                    start is TriplePattern.Binding && end is TriplePattern.Exact ->
                        ZeroOrMoreStatefulBindingExact(
                            start = start,
                            inner = inner,
                            end = end,
                        )

                    start is TriplePattern.Exact && end is TriplePattern.Binding ->
                        ZeroOrMoreStatefulExactBinding(
                            start = start,
                            inner = inner,
                            end = end,
                        )

                    start is TriplePattern.Exact && end is TriplePattern.Exact ->
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
            start: TriplePattern.Subject,
            predicate: TriplePattern.OneOrMore,
            end: TriplePattern.Object
        ): RepeatingPathState {
            return when (val inner = predicate.element) {
                is TriplePattern.StatelessPredicate -> when {
                    start is TriplePattern.Binding && end is TriplePattern.Binding ->
                        OneOrMoreStatelessBindings(
                            start = start,
                            inner = inner,
                            end = end,
                        )

                    start is TriplePattern.Binding && end is TriplePattern.Exact ->
                        OneOrMoreStatelessBindingExact(
                            start = start,
                            inner = inner,
                            end = end,
                        )

                    start is TriplePattern.Exact && end is TriplePattern.Binding ->
                        OneOrMoreStatelessExactBinding(
                            start = start,
                            inner = inner,
                            end = end,
                        )

                    start is TriplePattern.Exact && end is TriplePattern.Exact ->
                        OneOrMoreStatelessExact(
                            start = start,
                            inner = inner,
                            end = end,
                        )

                    else ->
                        throw IllegalStateException("Internal error: unknown subject / pattern combination for `OneOrMore` predicate construct: $start -> $end")
                }

                else -> when {
                    start is TriplePattern.Binding && end is TriplePattern.Binding ->
                        OneOrMoreStatefulBindings(
                            start = start,
                            inner = inner,
                            end = end,
                        )

                    start is TriplePattern.Binding && end is TriplePattern.Exact ->
                        OneOrMoreStatefulBindingExact(
                            start = start,
                            inner = inner,
                            end = end,
                        )

                    start is TriplePattern.Exact && end is TriplePattern.Binding ->
                        OneOrMoreStatefulExactBinding(
                            start = start,
                            inner = inner,
                            end = end,
                        )

                    start is TriplePattern.Exact && end is TriplePattern.Exact ->
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
