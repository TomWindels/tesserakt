package dev.tesserakt.sparql.runtime.query

import dev.tesserakt.rdf.types.Quad
import dev.tesserakt.sparql.newAnonymousBinding
import dev.tesserakt.sparql.runtime.collection.MappingArray
import dev.tesserakt.sparql.runtime.evaluation.DataAddition
import dev.tesserakt.sparql.runtime.evaluation.DataDeletion
import dev.tesserakt.sparql.runtime.evaluation.DataDelta
import dev.tesserakt.sparql.runtime.evaluation.context.QueryContext
import dev.tesserakt.sparql.runtime.evaluation.mapping.Mapping
import dev.tesserakt.sparql.runtime.evaluation.mapping.mappingOf
import dev.tesserakt.sparql.runtime.stream.*
import dev.tesserakt.sparql.types.TriplePattern
import dev.tesserakt.sparql.types.matches
import dev.tesserakt.sparql.util.*

sealed class RepeatingPathState {

    class ZeroOrMoreStatelessBindings(
        val context: QueryContext,
        val start: TriplePattern.Binding,
        val inner: TriplePattern.StatelessPredicate,
        val end: TriplePattern.Binding,
    ) : RepeatingPathState() {

        // all terms that have been discovered (count of "zero-length" segments)
        private val terms = Counter<Quad.Term>()
        private val segments = SegmentsList()
        private val arr = MappingArray(context, start.name, end.name)

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
                .mapTo(result) { mappingOf(context, start.name to it.start, end.name to it.end) }
            // as we're two bindings zero length, the quad's edges can also be null-length paths
            if (quad.s !in terms) {
                result.add(mappingOf(context, start.name to quad.s, end.name to quad.s))
            }
            if (quad.o !in terms) {
                result.add(mappingOf(context, start.name to quad.o, end.name to quad.o))
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
                .mapTo(result) { mappingOf(context, start.name to it.start, end.name to it.end) }
            if (terms[quad.s] == 1) {
                result.add(mappingOf(context, start.name to quad.s, end.name to quad.s))
            }
            if (terms[quad.o] == 1) {
                result.add(mappingOf(context, start.name to quad.o, end.name to quad.o))
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
        val context: QueryContext,
        val start: TriplePattern.Binding,
        inner: TriplePattern.Predicate,
        val end: TriplePattern.Binding,
    ) : RepeatingPathState() {

        private val segments = SegmentsList()
        // all terms that have been discovered (count of "zero-length" segments)
        private val terms = Counter<Quad.Term>()
        private val arr = MappingArray(context, start.name, end.name)
        private val inner = TriplePatternState.from(context, start, inner, end)

        override val cardinality: Cardinality
            get() = arr.cardinality

        override fun process(delta: DataDelta) {
            val quad = delta.value
            when (delta) {
                is DataAddition -> {
                    arr.addAll(peek(delta))
                    val new = inner.peek(delta)
                        .map { SegmentsList.Segment(start = it.get(context, start.name)!!, end = it.get(context, end.name)!!) }
                    segments.insert(new)
                    inner.process(delta)
                    terms.increment(quad.s)
                    terms.increment(quad.o)
                }

                is DataDeletion -> {
                    arr.removeAll(peek(delta))
                    val removed = inner.peek(delta)
                        .map { SegmentsList.Segment(start = it.get(context, start.name)!!, end = it.get(context, end.name)!!) }
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
                .map { SegmentsList.Segment(start = it.get(context, start.name)!!, end = it.get(context, end.name)!!) }
            // as it's possible for multiple segments to be returned from a single quad insertion, and this in turn
            //  cause some paths to come back in duplicates, we make it instantly distinct
            val result = mutableSetOf<Mapping>()
            segments.newPathsOnAdding(new.toSet())
                .forEach {
                    // ensuring zero lengths aren't included
                    if (it.start != it.end) {
                        result.add(mappingOf(context, start.name to it.start, end.name to it.end))
                    }
                }
            // as we're two bindings zero length, the quad's edges can also be null-length paths
            if (quad.s !in terms) {
                result.add(mappingOf(context, start.name to quad.s, end.name to quad.s))
            }
            if (quad.o !in terms) {
                result.add(mappingOf(context, start.name to quad.o, end.name to quad.o))
            }
            return result.toStream()
        }

        override fun peek(deletion: DataDeletion): Stream<Mapping> {
            val quad = deletion.value
            val removed = inner.peek(deletion)
                .map { SegmentsList.Segment(start = it.get(context, start.name)!!, end = it.get(context, end.name)!!) }
            // as it's possible for multiple segments to be returned from a single quad insertion, and this in turn
            //  cause some paths to come back in duplicates, we make it instantly distinct
            val result = mutableSetOf<Mapping>()
            segments.removedPathsOnRemoving(removed)
                .forEach {
                    // ensuring zero lengths aren't included
                    if (it.start != it.end) {
                        result.add(mappingOf(context, start.name to it.start, end.name to it.end))
                    }
                }
            if (terms[quad.s] == 1) {
                result.add(mappingOf(context, start.name to quad.s, end.name to quad.s))
            }
            if (terms[quad.o] == 1) {
                result.add(mappingOf(context, start.name to quad.o, end.name to quad.o))
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
        val context: QueryContext,
        val start: TriplePattern.Binding,
        val inner: TriplePattern.StatelessPredicate,
        val end: TriplePattern.Exact,
    ) : RepeatingPathState() {

        private val segments = SegmentsList()
        private val arr = MappingArray(context, start.name)

        override val cardinality: Cardinality
            get() = arr.cardinality

        init {
            // eval(Path(X:term, ZeroOrOnePath(P), Y:var)) = { (Y, yn) | yn = X or {(Y, yn)} in eval(Path(X,P,Y)) }
            arr.add(mappingOf(context, start.name to end.term))
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
                        result.add(mappingOf(context, start.name to it.start))
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
                        result.add(mappingOf(context, start.name to it.start))
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
        val context: QueryContext,
        val start: TriplePattern.Binding,
        inner: TriplePattern.Predicate,
        val end: TriplePattern.Exact,
    ) : RepeatingPathState() {

        private val segments = SegmentsList()
        private val arr = MappingArray(context, start.name)

        // "bridge" binding, responsible for keeping the inner predicate's end variable, allowing for more matches that
        //  in turn can produce additional results only obtainable by combining these additional matches; i.e.
        //  A -> B and B -> C should yield A -> C, which is only possible if we don't enforce an exact match B
        private val bridge = newAnonymousBinding()
        private val inner = TriplePatternState.from(context, start, inner, bridge)

        override val cardinality: Cardinality
            get() = arr.cardinality

        init {
            // eval(Path(X:term, ZeroOrOnePath(P), Y:var)) = { (Y, yn) | yn = X or {(Y, yn)} in eval(Path(X,P,Y)) }
            arr.add(mappingOf(context, start.name to end.term))
        }

        override fun process(delta: DataDelta) {
            when (delta) {
                is DataAddition -> {
                    arr.addAll(peek(delta))
                    segments.insert(
                        elements = inner
                            .peek(delta)
                            .map { SegmentsList.Segment(start = it.get(context, start.name)!!, end = it.get(context, bridge.name)!!) }
                    )
                }

                is DataDeletion -> {
                    arr.removeAll(peek(delta))
                    segments.remove(
                        elements = inner
                            .peek(delta)
                            .map { SegmentsList.Segment(start = it.get(context, start.name)!!, end = it.get(context, bridge.name)!!) }
                    )
                }
            }
            inner.process(delta)
        }

        override fun peek(addition: DataAddition): Stream<Mapping> {
            val new = inner.peek(addition)
                .mapTo(mutableSetOf()) { SegmentsList.Segment(start = it.get(context, start.name)!!, end = it.get(context, bridge.name)!!) }
                .ifEmpty { return emptyStream() }
            // as it's possible for multiple segments to be returned from a single quad insertion, and this in turn
            //  cause some paths to come back in duplicates, we make it instantly distinct
            val result = mutableSetOf<Mapping>()
            segments.newPathsOnAdding(new)
                .forEach {
                    if (end.matches(it.end) && !end.matches(it.start)) {
                        result.add(mappingOf(context, start.name to it.start))
                    }
                }
            return result.toStream()
        }

        override fun peek(deletion: DataDeletion): Stream<Mapping> {
            val removed = inner.peek(deletion)
                .map { SegmentsList.Segment(start = it.get(context, start.name)!!, end = it.get(context, bridge.name)!!) }
                .ifEmpty { return emptyStream() }
            // as it's possible for multiple segments to be returned from a single quad insertion, and this in turn
            //  cause some paths to come back in duplicates, we make it instantly distinct
            val result = mutableSetOf<Mapping>()
            segments.removedPathsOnRemoving(removed)
                .forEach {
                    // making sure we only include non-zero-length exact matches
                    if (end.matches(it.end) && !end.matches(it.start)) {
                        result.add(mappingOf(context, start.name to it.start))
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
        val context: QueryContext,
        val start: TriplePattern.Exact,
        val inner: TriplePattern.StatelessPredicate,
        val end: TriplePattern.Binding,
    ) : RepeatingPathState() {

        private val segments = SegmentsList()
        private val arr = MappingArray(context, end.name)

        override val cardinality: Cardinality
            get() = arr.cardinality

        init {
            // eval(Path(X:term, ZeroOrOnePath(P), Y:var)) = { (Y, yn) | yn = X or {(Y, yn)} in eval(Path(X,P,Y)) }
            arr.add(mappingOf(context, end.name to start.term))
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
                        result.add(mappingOf(context, end.name to it.end))
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
                        result.add(mappingOf(context, end.name to it.end))
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
        val context: QueryContext,
        val start: TriplePattern.Exact,
        inner: TriplePattern.Predicate,
        val end: TriplePattern.Binding,
    ) : RepeatingPathState() {

        private val segments = SegmentsList()
        private val arr = MappingArray(context, end.name)

        // "bridge" binding, responsible for keeping the inner predicate's end variable, allowing for more matches that
        //  in turn can produce additional results only obtainable by combining these additional matches; i.e.
        //  A -> B and B -> C should yield A -> C, which is only possible if we don't enforce an exact match B
        private val bridge = newAnonymousBinding()
        private val inner = TriplePatternState.from(context, bridge, inner, end)

        override val cardinality: Cardinality
            get() = arr.cardinality

        init {
            // eval(Path(X:term, ZeroOrOnePath(P), Y:var)) = { (Y, yn) | yn = X or {(Y, yn)} in eval(Path(X,P,Y)) }
            arr.add(mappingOf(context, end.name to start.term))
        }

        override fun process(delta: DataDelta) {
            when (delta) {
                is DataAddition -> {
                    arr.addAll(peek(delta))
                    segments.insert(
                        elements = inner
                            .peek(delta)
                            .map { SegmentsList.Segment(start = it.get(context, bridge.name)!!, end = it.get(context, end.name)!!) }
                    )
                }

                is DataDeletion -> {
                    arr.removeAll(peek(delta))
                    segments.remove(
                        elements = inner
                            .peek(delta)
                            .map { SegmentsList.Segment(start = it.get(context, bridge.name)!!, end = it.get(context, end.name)!!) }
                    )
                }
            }
            inner.process(delta)
        }

        override fun peek(addition: DataAddition): Stream<Mapping> {
            val new = inner.peek(addition)
                .mapTo(mutableSetOf()) { SegmentsList.Segment(start = it.get(context, bridge.name)!!, end = it.get(context, end.name)!!) }
                .ifEmpty { return emptyStream() }
            // as it's possible for multiple segments to be returned from a single quad insertion, and this in turn
            //  cause some paths to come back in duplicates, we make it instantly distinct
            val result = mutableSetOf<Mapping>()
            segments.newPathsOnAdding(new)
                .forEach {
                    if (start.matches(it.start) && !start.matches(it.end)) {
                        result.add(mappingOf(context, end.name to it.end))
                    }
                }
            return result.toStream()
        }

        override fun peek(deletion: DataDeletion): Stream<Mapping> {
            val removed = inner.peek(deletion)
                .map { SegmentsList.Segment(start = it.get(context, bridge.name)!!, end = it.get(context, end.name)!!) }
                .ifEmpty { return emptyStream() }
            // as it's possible for multiple segments to be returned from a single quad insertion, and this in turn
            //  cause some paths to come back in duplicates, we make it instantly distinct
            val result = mutableSetOf<Mapping>()
            segments.removedPathsOnRemoving(removed)
                .forEach {
                    if (start.matches(it.start) && !start.matches(it.end)) {
                        result.add(mappingOf(context, end.name to it.end))
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
        val context: QueryContext,
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
                    return streamOf(context.emptyMapping())
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
                    return streamOf(context.emptyMapping())
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
        val context: QueryContext,
        val start: TriplePattern.Exact,
        inner: TriplePattern.Predicate,
        val end: TriplePattern.Exact
    ) : RepeatingPathState() {

        private var satisfied = start == end

        // "bridge" bindings, responsible for keeping the inner predicate's connection points variable, allowing for
        //  more matches that in turn can produce additional results only obtainable by combining these additional
        //  matches; i.e.
        //  A -> B and B -> C should yield A -> C, which is only possible if we don't enforce an exact match B
        private val intermediateStart = newAnonymousBinding()
        private val intermediateEnd = newAnonymousBinding()
        private val inner = TriplePatternState.from(context, intermediateStart, inner, intermediateEnd)
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
                                start = it.get(context, intermediateStart.name)!!,
                                end = it.get(context, intermediateEnd.name)!!
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
                                start = it.get(context, intermediateStart.name)!!,
                                end = it.get(context, intermediateEnd.name)!!
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
                        start = it.get(context, intermediateStart.name)!!,
                        end = it.get(context, intermediateEnd.name)!!
                    )
                }
            if (segments.newPathsOnAdding(added).any { it.start == start.term && it.end == end.term }) {
                return streamOf(context.emptyMapping())
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
                        start = it.get(context, intermediateStart.name)!!,
                        end = it.get(context, intermediateEnd.name)!!
                    )
                }
            if (
                segments
                    .remainingPathsOnRemoving(removed)
                    .none { it.start == start.term && it.end == end.term }
            ) {
                return streamOf(context.emptyMapping())
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
        val context: QueryContext,
        val start: TriplePattern.Binding,
        val inner: TriplePattern.StatelessPredicate,
        val end: TriplePattern.Binding,
    ) : RepeatingPathState() {

        private val segments = SegmentsList()
        private val arr = MappingArray(context, start.name, end.name)

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
                .mapTo(result) { mappingOf(context, start.name to it.start, end.name to it.end) }
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
        val context: QueryContext,
        val start: TriplePattern.Binding,
        inner: TriplePattern.Predicate,
        val end: TriplePattern.Binding,
    ) : RepeatingPathState() {

        private val segments = SegmentsList()
        private val arr = MappingArray(context, start.name, end.name)
        private val inner = TriplePatternState.from(context, start, inner, end)

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
                .mapTo(result) { mappingOf(context, start.name to it.start, end.name to it.end) }
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
                .mapTo(mutableSetOf()) { SegmentsList.Segment(start = it.get(context, start.name)!!, end = it.get(context, end.name)!!) }
        }

    }

    class OneOrMoreStatelessBindingExact(
        val context: QueryContext,
        val start: TriplePattern.Binding,
        val inner: TriplePattern.StatelessPredicate,
        val end: TriplePattern.Exact,
    ) : RepeatingPathState() {

        private val segments = SegmentsList()
        private val arr = MappingArray(context, start.name)

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
                .mapTo(result) { mappingOf(context, start.name to it) }
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
        val context: QueryContext,
        val start: TriplePattern.Binding,
        inner: TriplePattern.Predicate,
        val end: TriplePattern.Exact,
    ) : RepeatingPathState() {

        private val segments = SegmentsList()
        private val arr = MappingArray(context, start.name)

        // "bridge" binding, responsible for keeping the inner predicate's end variable, allowing for more matches that
        //  in turn can produce additional results only obtainable by combining these additional matches; i.e.
        //  A -> B and B -> C should yield A -> C, which is only possible if we don't enforce an exact match B
        private val bridge = newAnonymousBinding()
        private val inner = TriplePatternState.from(context, start, inner, bridge)

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
                    arr.addAll(peeked.map { mappingOf(context, start.name to it) })
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
            return result.map { mappingOf(context, start.name to it) }.toStream()
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
                .mapTo(mutableSetOf()) { SegmentsList.Segment(start = it.get(context, start.name)!!, end = it.get(context, bridge.name)!!) }
        }

    }

    class OneOrMoreStatelessExactBinding(
        val context: QueryContext,
        val start: TriplePattern.Exact,
        val inner: TriplePattern.StatelessPredicate,
        val end: TriplePattern.Binding,
    ) : RepeatingPathState() {

        private val segments = SegmentsList()
        private val arr = MappingArray(context, end.name)

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
                .mapTo(result) { mappingOf(context, end.name to it) }
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
        val context: QueryContext,
        val start: TriplePattern.Exact,
        inner: TriplePattern.Predicate,
        val end: TriplePattern.Binding,
    ) : RepeatingPathState() {

        private val segments = SegmentsList()
        private val arr = MappingArray(context, end.name)

        // "bridge" binding, responsible for keeping the inner predicate's end variable, allowing for more matches that
        //  in turn can produce additional results only obtainable by combining these additional matches; i.e.
        //  A -> B and B -> C should yield A -> C, which is only possible if we don't enforce an exact match B
        private val bridge = newAnonymousBinding()
        private val inner = TriplePatternState.from(context, bridge, inner, end)

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
                    arr.addAll(peeked.map { mappingOf(context, end.name to it) })
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
            return result.map { mappingOf(context, end.name to it) }.toStream()
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
                .mapTo(mutableSetOf()) { SegmentsList.Segment(start = it.get(context, bridge.name)!!, end = it.get(context, end.name)!!) }
        }

    }

    class OneOrMoreStatelessExact(
        val context: QueryContext,
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
                streamOf(context.emptyMapping())
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
        val context: QueryContext,
        val start: TriplePattern.Exact,
        inner: TriplePattern.Predicate,
        val end: TriplePattern.Exact
    ) : RepeatingPathState() {

        private var satisfied = false

        // "bridge" bindings, responsible for keeping the inner predicate's connection points variable, allowing for
        //  more matches that in turn can produce additional results only obtainable by combining these additional
        //  matches; i.e.
        //  A -> B and B -> C should yield A -> C, which is only possible if we don't enforce an exact match B
        private val intermediateStart = newAnonymousBinding()
        private val intermediateEnd = newAnonymousBinding()
        private val inner = TriplePatternState.from(context, intermediateStart, inner, intermediateEnd)
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
                                start = it.get(context, intermediateStart.name)!!,
                                end = it.get(context, intermediateEnd.name)!!
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
                            start = it.get(context, intermediateStart.name)!!,
                            end = it.get(context, intermediateEnd.name)!!
                        )
                    }
                if (segments.newPathsOnAdding(new).any { it.start == start.term && it.end == end.term }) {
                    return streamOf(context.emptyMapping())
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
            context: QueryContext,
            start: TriplePattern.Subject,
            predicate: TriplePattern.ZeroOrMore,
            end: TriplePattern.Object
        ): RepeatingPathState {
            return when (val inner = predicate.element) {
                is TriplePattern.StatelessPredicate -> when {
                    start is TriplePattern.Binding && end is TriplePattern.Binding ->
                        ZeroOrMoreStatelessBindings(
                            context = context,
                            start = start,
                            inner = inner,
                            end = end,
                        )

                    start is TriplePattern.Binding && end is TriplePattern.Exact ->
                        ZeroOrMoreStatelessBindingExact(
                            context = context,
                            start = start,
                            inner = inner,
                            end = end,
                        )

                    start is TriplePattern.Exact && end is TriplePattern.Binding ->
                        ZeroOrMoreStatelessExactBinding(
                            context = context,
                            start = start,
                            inner = inner,
                            end = end,
                        )

                    start is TriplePattern.Exact && end is TriplePattern.Exact ->
                        ZeroOrMoreStatelessExact(
                            context = context,
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
                            context = context,
                            start = start,
                            inner = inner,
                            end = end,
                        )

                    start is TriplePattern.Binding && end is TriplePattern.Exact ->
                        ZeroOrMoreStatefulBindingExact(
                            context = context,
                            start = start,
                            inner = inner,
                            end = end,
                        )

                    start is TriplePattern.Exact && end is TriplePattern.Binding ->
                        ZeroOrMoreStatefulExactBinding(
                            context = context,
                            start = start,
                            inner = inner,
                            end = end,
                        )

                    start is TriplePattern.Exact && end is TriplePattern.Exact ->
                        ZeroOrMoreStatefulExact(
                            context = context,
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
            context: QueryContext,
            start: TriplePattern.Subject,
            predicate: TriplePattern.OneOrMore,
            end: TriplePattern.Object
        ): RepeatingPathState {
            return when (val inner = predicate.element) {
                is TriplePattern.StatelessPredicate -> when {
                    start is TriplePattern.Binding && end is TriplePattern.Binding ->
                        OneOrMoreStatelessBindings(
                            context = context,
                            start = start,
                            inner = inner,
                            end = end,
                        )

                    start is TriplePattern.Binding && end is TriplePattern.Exact ->
                        OneOrMoreStatelessBindingExact(
                            context = context,
                            start = start,
                            inner = inner,
                            end = end,
                        )

                    start is TriplePattern.Exact && end is TriplePattern.Binding ->
                        OneOrMoreStatelessExactBinding(
                            context = context,
                            start = start,
                            inner = inner,
                            end = end,
                        )

                    start is TriplePattern.Exact && end is TriplePattern.Exact ->
                        OneOrMoreStatelessExact(
                            context = context,
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
                            context = context,
                            start = start,
                            inner = inner,
                            end = end,
                        )

                    start is TriplePattern.Binding && end is TriplePattern.Exact ->
                        OneOrMoreStatefulBindingExact(
                            context = context,
                            start = start,
                            inner = inner,
                            end = end,
                        )

                    start is TriplePattern.Exact && end is TriplePattern.Binding ->
                        OneOrMoreStatefulExactBinding(
                            context = context,
                            start = start,
                            inner = inner,
                            end = end,
                        )

                    start is TriplePattern.Exact && end is TriplePattern.Exact ->
                        OneOrMoreStatefulExact(
                            context = context,
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
