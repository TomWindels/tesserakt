package dev.tesserakt.sparql.runtime.query

import dev.tesserakt.rdf.types.EncodedQuad
import dev.tesserakt.sparql.runtime.collection.MappingArrayHint
import dev.tesserakt.sparql.runtime.collection.ReindexableMappingArray
import dev.tesserakt.sparql.runtime.evaluation.*
import dev.tesserakt.sparql.runtime.evaluation.context.QueryContext
import dev.tesserakt.sparql.runtime.evaluation.mapping.Mapping
import dev.tesserakt.sparql.runtime.evaluation.mapping.mappingOf
import dev.tesserakt.sparql.runtime.stream.*
import dev.tesserakt.sparql.util.*

sealed class RepeatingPathState {

    class ZeroOrMoreStatelessBindings(
        val context: QueryContext,
        val start: TriplePatternState.Binding,
        val inner: TriplePatternState.StatelessPredicate,
        val end: TriplePatternState.Binding,
    ) : RepeatingPathState() {

        // all terms that have been discovered (count of "zero-length" segments)
        private val terms = Counter<TermIdentifier>()
        private val segments = SegmentsList()
        private val arr = ReindexableMappingArray(start.id, end.id)

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
                    terms.increment(TermIdentifier(quad.s))
                    terms.increment(TermIdentifier(quad.o))
                }

                is DataDeletion -> {
                    arr.removeAll(peek(delta))
                    segments.remove(quad.toSegment())
                    // two bindings, so removing both ends
                    terms.decrement(TermIdentifier(quad.s))
                    terms.decrement(TermIdentifier(quad.o))
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
                .mapTo(result) { mappingOf(context, start.id to it.start, end.id to it.end) }
            // as we're two bindings zero length, the quad's edges can also be null-length paths
            if (TermIdentifier(quad.s) !in terms) {
                result.add(mappingOf(context, start.id to TermIdentifier(quad.s), end.id to TermIdentifier(quad.s)))
            }
            if (TermIdentifier(quad.o) !in terms) {
                result.add(mappingOf(context, start.id to TermIdentifier(quad.o), end.id to TermIdentifier(quad.o)))
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
                .mapTo(result) { mappingOf(context, start.id to it.start, end.id to it.end) }
            if (terms[TermIdentifier(quad.s)] == 1) {
                result.add(mappingOf(context, start.id to TermIdentifier(quad.s), end.id to TermIdentifier(quad.s)))
            }
            if (terms[TermIdentifier(quad.o)] == 1) {
                result.add(mappingOf(context, start.id to TermIdentifier(quad.o), end.id to TermIdentifier(quad.o)))
            }
            return result.toStream()
        }

        override fun join(mappings: OptimisedStream<Mapping>): Stream<Mapping> {
            return arr.join(mappings)
        }

        override fun join(mappings: OptimisedStream<Mapping>, ignore: Iterable<Mapping>): Stream<Mapping> {
            return mappings.transform(maxCardinality = arr.cardinality) { mapping -> arr.iter(mapping).remove(ignore).join(mapping) }
        }

        override fun reindex(bindings: BindingIdentifierSet, hint: MappingArrayHint) {
            arr.reindex(bindings, hint)
        }

        override fun toString() = segments.toString()

    }

    class ZeroOrMoreStatefulBindings(
        val context: QueryContext,
        val start: TriplePatternState.Binding,
        inner: TriplePatternState.Predicate,
        val end: TriplePatternState.Binding,
    ) : RepeatingPathState() {

        private val segments = SegmentsList()
        // all terms that have been discovered (count of "zero-length" segments)
        private val terms = Counter<TermIdentifier>()
        private val arr = ReindexableMappingArray(start.id, end.id)
        private val inner = TriplePatternState.from(context, start, inner, end)

        override val cardinality: Cardinality
            get() = arr.cardinality

        override fun process(delta: DataDelta) {
            val quad = delta.value
            when (delta) {
                is DataAddition -> {
                    arr.addAll(peek(delta))
                    val new = inner.peek(delta)
                        .map { SegmentsList.Segment(start = it.get(start.id)!!, end = it.get(end.id)!!) }
                    segments.insert(new)
                    inner.process(delta)
                    terms.increment(TermIdentifier(quad.s))
                    terms.increment(TermIdentifier(quad.o))
                }

                is DataDeletion -> {
                    arr.removeAll(peek(delta))
                    val removed = inner.peek(delta)
                        .map { SegmentsList.Segment(start = it.get(start.id)!!, end = it.get(end.id)!!) }
                    segments.remove(removed)
                    inner.process(delta)
                    terms.decrement(TermIdentifier(quad.s))
                    terms.decrement(TermIdentifier(quad.o))
                }
            }
        }

        override fun peek(addition: DataAddition): Stream<Mapping> {
            val quad = addition.value
            val new = inner.peek(addition)
                .map { SegmentsList.Segment(start = it.get(start.id)!!, end = it.get(end.id)!!) }
            // as it's possible for multiple segments to be returned from a single quad insertion, and this in turn
            //  cause some paths to come back in duplicates, we make it instantly distinct
            val result = mutableSetOf<Mapping>()
            segments.newPathsOnAdding(new.toSet())
                .forEach {
                    // ensuring zero lengths aren't included
                    if (it.start != it.end) {
                        result.add(mappingOf(context, start.id to it.start, end.id to it.end))
                    }
                }
            // as we're two bindings zero length, the quad's edges can also be null-length paths
            if (TermIdentifier(quad.s) !in terms) {
                result.add(mappingOf(context, start.id to TermIdentifier(quad.s), end.id to TermIdentifier(quad.s)))
            }
            if (TermIdentifier(quad.o) !in terms) {
                result.add(mappingOf(context, start.id to TermIdentifier(quad.o), end.id to TermIdentifier(quad.o)))
            }
            return result.toStream()
        }

        override fun peek(deletion: DataDeletion): Stream<Mapping> {
            val quad = deletion.value
            val removed = inner.peek(deletion)
                .map { SegmentsList.Segment(start = it.get(start.id)!!, end = it.get(end.id)!!) }
            // as it's possible for multiple segments to be returned from a single quad insertion, and this in turn
            //  cause some paths to come back in duplicates, we make it instantly distinct
            val result = mutableSetOf<Mapping>()
            segments.removedPathsOnRemoving(removed)
                .forEach {
                    // ensuring zero lengths aren't included
                    if (it.start != it.end) {
                        result.add(mappingOf(context, start.id to it.start, end.id to it.end))
                    }
                }
            if (terms[TermIdentifier(quad.s)] == 1) {
                result.add(mappingOf(context, start.id to TermIdentifier(quad.s), end.id to TermIdentifier(quad.s)))
            }
            if (terms[TermIdentifier(quad.o)] == 1) {
                result.add(mappingOf(context, start.id to TermIdentifier(quad.o), end.id to TermIdentifier(quad.o)))
            }
            return result.toStream()
        }

        override fun join(mappings: OptimisedStream<Mapping>): Stream<Mapping> {
            return arr.join(mappings)
        }

        override fun join(mappings: OptimisedStream<Mapping>, ignore: Iterable<Mapping>): Stream<Mapping> {
            return mappings.transform(maxCardinality = arr.cardinality) { mapping -> arr.iter(mapping).remove(ignore).join(mapping) }
        }

        override fun reindex(bindings: BindingIdentifierSet, hint: MappingArrayHint) {
            arr.reindex(bindings, hint)
        }

        override fun toString() = segments.toString()

    }

    class ZeroOrMoreStatelessBindingExact(
        val context: QueryContext,
        val start: TriplePatternState.Binding,
        val inner: TriplePatternState.StatelessPredicate,
        val end: TriplePatternState.Exact,
    ) : RepeatingPathState() {

        private val segments = SegmentsList()
        private val arr = ReindexableMappingArray(start.id)

        override val cardinality: Cardinality
            get() = arr.cardinality

        init {
            // eval(Path(X:term, ZeroOrOnePath(P), Y:var)) = { (Y, yn) | yn = X or {(Y, yn)} in eval(Path(X,P,Y)) }
            arr.add(mappingOf(context, start.id to end.id))
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
                        result.add(mappingOf(context, start.id to it.start))
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
                        result.add(mappingOf(context, start.id to it.start))
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

        override fun reindex(bindings: BindingIdentifierSet, hint: MappingArrayHint) {
            arr.reindex(bindings, hint)
        }

        override fun toString() = segments.toString()

    }

    class ZeroOrMoreStatefulBindingExact(
        val context: QueryContext,
        val start: TriplePatternState.Binding,
        inner: TriplePatternState.Predicate,
        val end: TriplePatternState.Exact,
    ) : RepeatingPathState() {

        private val segments = SegmentsList()
        private val arr = ReindexableMappingArray(start.id)

        // "bridge" binding, responsible for keeping the inner predicate's end variable, allowing for more matches that
        //  in turn can produce additional results only obtainable by combining these additional matches; i.e.
        //  A -> B and B -> C should yield A -> C, which is only possible if we don't enforce an exact match B
        private val bridge = TriplePatternState.Binding(BindingIdentifier(context.newAnonymousBinding()))
        private val inner = TriplePatternState.from(context, start, inner, bridge)

        override val cardinality: Cardinality
            get() = arr.cardinality

        init {
            // eval(Path(X:term, ZeroOrOnePath(P), Y:var)) = { (Y, yn) | yn = X or {(Y, yn)} in eval(Path(X,P,Y)) }
            arr.add(mappingOf(context, start.id to end.id))
        }

        override fun process(delta: DataDelta) {
            when (delta) {
                is DataAddition -> {
                    arr.addAll(peek(delta))
                    segments.insert(
                        elements = inner
                            .peek(delta)
                            .map { SegmentsList.Segment(start = it.get(start.id)!!, end = it.get(bridge.id)!!) }
                    )
                }

                is DataDeletion -> {
                    arr.removeAll(peek(delta))
                    segments.remove(
                        elements = inner
                            .peek(delta)
                            .map { SegmentsList.Segment(start = it.get(start.id)!!, end = it.get(bridge.id)!!) }
                    )
                }
            }
            inner.process(delta)
        }

        override fun peek(addition: DataAddition): Stream<Mapping> {
            val new = inner.peek(addition)
                .mapTo(mutableSetOf()) { SegmentsList.Segment(start = it.get(start.id)!!, end = it.get(bridge.id)!!) }
                .ifEmpty { return emptyStream() }
            // as it's possible for multiple segments to be returned from a single quad insertion, and this in turn
            //  cause some paths to come back in duplicates, we make it instantly distinct
            val result = mutableSetOf<Mapping>()
            segments.newPathsOnAdding(new)
                .forEach {
                    if (end.matches(it.end) && !end.matches(it.start)) {
                        result.add(mappingOf(context, start.id to it.start))
                    }
                }
            return result.toStream()
        }

        override fun peek(deletion: DataDeletion): Stream<Mapping> {
            val removed = inner.peek(deletion)
                .map { SegmentsList.Segment(start = it.get(start.id)!!, end = it.get(bridge.id)!!) }
                .ifEmpty { return emptyStream() }
            // as it's possible for multiple segments to be returned from a single quad insertion, and this in turn
            //  cause some paths to come back in duplicates, we make it instantly distinct
            val result = mutableSetOf<Mapping>()
            segments.removedPathsOnRemoving(removed)
                .forEach {
                    // making sure we only include non-zero-length exact matches
                    if (end.matches(it.end) && !end.matches(it.start)) {
                        result.add(mappingOf(context, start.id to it.start))
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

        override fun reindex(bindings: BindingIdentifierSet, hint: MappingArrayHint) {
            arr.reindex(bindings, hint)
        }

        override fun toString() = segments.toString()

    }

    class ZeroOrMoreStatelessExactBinding(
        val context: QueryContext,
        val start: TriplePatternState.Exact,
        val inner: TriplePatternState.StatelessPredicate,
        val end: TriplePatternState.Binding,
    ) : RepeatingPathState() {

        private val segments = SegmentsList()
        private val arr = ReindexableMappingArray(end.id)

        override val cardinality: Cardinality
            get() = arr.cardinality

        init {
            // eval(Path(X:term, ZeroOrOnePath(P), Y:var)) = { (Y, yn) | yn = X or {(Y, yn)} in eval(Path(X,P,Y)) }
            arr.add(mappingOf(context, end.id to start.id))
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
                        result.add(mappingOf(context, end.id to it.end))
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
                        result.add(mappingOf(context, end.id to it.end))
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

        override fun reindex(bindings: BindingIdentifierSet, hint: MappingArrayHint) {
            arr.reindex(bindings, hint)
        }

        override fun toString() = segments.toString()

    }

    class ZeroOrMoreStatefulExactBinding(
        val context: QueryContext,
        val start: TriplePatternState.Exact,
        inner: TriplePatternState.Predicate,
        val end: TriplePatternState.Binding,
    ) : RepeatingPathState() {

        private val segments = SegmentsList()
        private val arr = ReindexableMappingArray(end.id)

        // "bridge" binding, responsible for keeping the inner predicate's end variable, allowing for more matches that
        //  in turn can produce additional results only obtainable by combining these additional matches; i.e.
        //  A -> B and B -> C should yield A -> C, which is only possible if we don't enforce an exact match B
        private val bridge = TriplePatternState.Binding(BindingIdentifier(context.newAnonymousBinding()))
        private val inner = TriplePatternState.from(context, bridge, inner, end)

        override val cardinality: Cardinality
            get() = arr.cardinality

        init {
            // eval(Path(X:term, ZeroOrOnePath(P), Y:var)) = { (Y, yn) | yn = X or {(Y, yn)} in eval(Path(X,P,Y)) }
            arr.add(mappingOf(context, end.id to start.id))
        }

        override fun process(delta: DataDelta) {
            when (delta) {
                is DataAddition -> {
                    arr.addAll(peek(delta))
                    segments.insert(
                        elements = inner
                            .peek(delta)
                            .map { SegmentsList.Segment(start = it.get(bridge.id)!!, end = it.get(end.id)!!) }
                    )
                }

                is DataDeletion -> {
                    arr.removeAll(peek(delta))
                    segments.remove(
                        elements = inner
                            .peek(delta)
                            .map { SegmentsList.Segment(start = it.get(bridge.id)!!, end = it.get(end.id)!!) }
                    )
                }
            }
            inner.process(delta)
        }

        override fun peek(addition: DataAddition): Stream<Mapping> {
            val new = inner.peek(addition)
                .mapTo(mutableSetOf()) { SegmentsList.Segment(start = it.get(bridge.id)!!, end = it.get(end.id)!!) }
                .ifEmpty { return emptyStream() }
            // as it's possible for multiple segments to be returned from a single quad insertion, and this in turn
            //  cause some paths to come back in duplicates, we make it instantly distinct
            val result = mutableSetOf<Mapping>()
            segments.newPathsOnAdding(new)
                .forEach {
                    if (start.matches(it.start) && !start.matches(it.end)) {
                        result.add(mappingOf(context, end.id to it.end))
                    }
                }
            return result.toStream()
        }

        override fun peek(deletion: DataDeletion): Stream<Mapping> {
            val removed = inner.peek(deletion)
                .map { SegmentsList.Segment(start = it.get(bridge.id)!!, end = it.get(end.id)!!) }
                .ifEmpty { return emptyStream() }
            // as it's possible for multiple segments to be returned from a single quad insertion, and this in turn
            //  cause some paths to come back in duplicates, we make it instantly distinct
            val result = mutableSetOf<Mapping>()
            segments.removedPathsOnRemoving(removed)
                .forEach {
                    if (start.matches(it.start) && !start.matches(it.end)) {
                        result.add(mappingOf(context, end.id to it.end))
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

        override fun reindex(bindings: BindingIdentifierSet, hint: MappingArrayHint) {
            arr.reindex(bindings, hint)
        }

        override fun toString() = segments.toString()

    }

    class ZeroOrMoreStatelessExact(
        val context: QueryContext,
        val start: TriplePatternState.Exact,
        val inner: TriplePatternState.StatelessPredicate,
        val end: TriplePatternState.Exact
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
                    satisfied = satisfied || segments.paths.any { it.start == start.id && it.end == end.id }
                }

                is DataDeletion -> {
                    // removing the segment
                    segments.remove(quad.toSegment())
                    // using the updated segment state to update our satisfied state
                    // writing the logic like this so the check is short-circuited when possible
                    satisfied = satisfied && segments.paths.any { it.start == start.id && it.end == end.id }
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
                if (new.any { it.start == start.id && it.end == end.id }) {
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
                if (remaining.none { it.start == start.id && it.end == end.id }) {
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

        override fun reindex(bindings: BindingIdentifierSet, hint: MappingArrayHint) {
            // ignored
        }

    }

    class ZeroOrMoreStatefulExact(
        val context: QueryContext,
        val start: TriplePatternState.Exact,
        inner: TriplePatternState.Predicate,
        val end: TriplePatternState.Exact
    ) : RepeatingPathState() {

        private var satisfied = start == end

        // "bridge" bindings, responsible for keeping the inner predicate's connection points variable, allowing for
        //  more matches that in turn can produce additional results only obtainable by combining these additional
        //  matches; i.e.
        //  A -> B and B -> C should yield A -> C, which is only possible if we don't enforce an exact match B
        private val intermediateStart = TriplePatternState.Binding(BindingIdentifier(context.newAnonymousBinding()))
        private val intermediateEnd = TriplePatternState.Binding(BindingIdentifier(context.newAnonymousBinding()))
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
                                start = it.get(intermediateStart.id)!!,
                                end = it.get(intermediateEnd.id)!!
                            )
                        }
                    satisfied = satisfied || segments
                        .newPathsOnAdding(new.toSet())
                        .any { it.start == start.id && it.end == end.id }
                    inner.process(delta)
                    segments.insert(new)
                }

                is DataDeletion -> {
                    val peek = inner.peek(delta)
                    val removed = peek
                        .map {
                            SegmentsList.Segment(
                                start = it.get(intermediateStart.id)!!,
                                end = it.get(intermediateEnd.id)!!
                            )
                        }
                    satisfied = satisfied && segments
                        .remainingPathsOnRemoving(removed)
                        .any { it.start == start.id && it.end == end.id }
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
                        start = it.get(intermediateStart.id)!!,
                        end = it.get(intermediateEnd.id)!!
                    )
                }
            if (segments.newPathsOnAdding(added).any { it.start == start.id && it.end == end.id }) {
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
                        start = it.get(intermediateStart.id)!!,
                        end = it.get( intermediateEnd.id)!!
                    )
                }
            if (
                segments
                    .remainingPathsOnRemoving(removed)
                    .none { it.start == start.id && it.end == end.id }
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

        override fun reindex(bindings: BindingIdentifierSet, hint: MappingArrayHint) {
            // ignored
        }

    }

    class OneOrMoreStatelessBindings(
        val context: QueryContext,
        val start: TriplePatternState.Binding,
        val inner: TriplePatternState.StatelessPredicate,
        val end: TriplePatternState.Binding,
    ) : RepeatingPathState() {

        private val segments = SegmentsList()
        private val arr = ReindexableMappingArray(start.id, end.id)

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
                .mapTo(result) { mappingOf(context, start.id to it.start, end.id to it.end) }
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

        override fun reindex(bindings: BindingIdentifierSet, hint: MappingArrayHint) {
            arr.reindex(bindings, hint)
        }

        override fun toString() = segments.toString()

    }

    class OneOrMoreStatefulBindings(
        val context: QueryContext,
        val start: TriplePatternState.Binding,
        inner: TriplePatternState.Predicate,
        val end: TriplePatternState.Binding,
    ) : RepeatingPathState() {

        private val segments = SegmentsList()
        private val arr = ReindexableMappingArray(start.id, end.id)
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
                .mapTo(result) { mappingOf(context, start.id to it.start, end.id to it.end) }
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

        override fun reindex(bindings: BindingIdentifierSet, hint: MappingArrayHint) {
            arr.reindex(bindings, hint)
        }

        override fun toString() = segments.toString()

        private fun getNewSegments(quad: EncodedQuad): Set<SegmentsList.Segment> {
            return inner.peek(DataAddition(quad))
                .mapTo(mutableSetOf()) { SegmentsList.Segment(start = it.get(start.id)!!, end = it.get(end.id)!!) }
        }

    }

    class OneOrMoreStatelessBindingExact(
        val context: QueryContext,
        val start: TriplePatternState.Binding,
        val inner: TriplePatternState.StatelessPredicate,
        val end: TriplePatternState.Exact,
    ) : RepeatingPathState() {

        private val segments = SegmentsList()
        private val arr = ReindexableMappingArray(start.id)

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
                .mapTo(result) { mappingOf(context, start.id to it) }
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

        override fun reindex(bindings: BindingIdentifierSet, hint: MappingArrayHint) {
            arr.reindex(bindings, hint)
        }

        override fun toString() = segments.toString()

    }

    class OneOrMoreStatefulBindingExact(
        val context: QueryContext,
        val start: TriplePatternState.Binding,
        inner: TriplePatternState.Predicate,
        val end: TriplePatternState.Exact,
    ) : RepeatingPathState() {

        private val segments = SegmentsList()
        private val arr = ReindexableMappingArray(start.id)

        // "bridge" binding, responsible for keeping the inner predicate's end variable, allowing for more matches that
        //  in turn can produce additional results only obtainable by combining these additional matches; i.e.
        //  A -> B and B -> C should yield A -> C, which is only possible if we don't enforce an exact match B
        private val bridge = TriplePatternState.Binding(BindingIdentifier(context.newAnonymousBinding()))
        private val inner = TriplePatternState.from(context, start, inner, bridge)

        // all terms that were reached thus far (= new paths where end == exact end), kept track of separately as the
        //  use of the bridge binding makes the path state of the segment list unreliable w/o extra checking
        //  (inner repeating paths may return too many results due to the bridge binding)
        private val reached = mutableSetOf<TermIdentifier>()

        override val cardinality: Cardinality
            get() = arr.cardinality

        override fun process(delta: DataDelta) {
            val quad = delta.value
            when (delta) {
                is DataAddition -> {
                    val peeked = peekNewlyReachable(quad)
                    arr.addAll(peeked.map { mappingOf(context, start.id to it) })
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
            return result.map { mappingOf(context, start.id to it) }.toStream()
        }

        override fun peek(deletion: DataDeletion): Stream<Mapping> {
            TODO("Not yet implemented")
        }

        private fun peekNewlyReachable(quad: EncodedQuad): Set<TermIdentifier> {
            val new = getNewSegments(quad)
            // as it's possible for multiple segments to be returned from a single quad insertion, and this in turn
            //  cause some paths to come back in duplicates, we make it instantly distinct
            val result = mutableSetOf<TermIdentifier>()
            segments.newPathsOnAdding(new)
                .forEach {
                    if (it.end != end.id) {
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

        override fun reindex(bindings: BindingIdentifierSet, hint: MappingArrayHint) {
            arr.reindex(bindings, hint)
        }

        override fun toString() = segments.toString()

        private fun getNewSegments(quad: EncodedQuad): Set<SegmentsList.Segment> {
            return inner.peek(DataAddition(quad))
                .mapTo(mutableSetOf()) { SegmentsList.Segment(start = it.get(start.id)!!, end = it.get(bridge.id)!!) }
        }

    }

    class OneOrMoreStatelessExactBinding(
        val context: QueryContext,
        val start: TriplePatternState.Exact,
        val inner: TriplePatternState.StatelessPredicate,
        val end: TriplePatternState.Binding,
    ) : RepeatingPathState() {

        private val segments = SegmentsList()
        private val arr = ReindexableMappingArray(end.id)

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
                .mapTo(result) { mappingOf(context, end.id to it) }
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

        override fun reindex(bindings: BindingIdentifierSet, hint: MappingArrayHint) {
            arr.reindex(bindings, hint)
        }

        override fun toString() = segments.toString()

    }

    class OneOrMoreStatefulExactBinding(
        val context: QueryContext,
        val start: TriplePatternState.Exact,
        inner: TriplePatternState.Predicate,
        val end: TriplePatternState.Binding,
    ) : RepeatingPathState() {

        private val segments = SegmentsList()
        private val arr = ReindexableMappingArray(end.id)

        // "bridge" binding, responsible for keeping the inner predicate's end variable, allowing for more matches that
        //  in turn can produce additional results only obtainable by combining these additional matches; i.e.
        //  A -> B and B -> C should yield A -> C, which is only possible if we don't enforce an exact match B
        private val bridge = TriplePatternState.Binding(BindingIdentifier(context.newAnonymousBinding()))
        private val inner = TriplePatternState.from(context, bridge, inner, end)

        // all terms that were reached thus far (= new paths where end == exact end), kept track of separately as the
        //  use of the bridge binding makes the path state of the segment list unreliable w/o extra checking
        //  (inner repeating paths may return too many results due to the bridge binding)
        private val reached = mutableSetOf<TermIdentifier>()

        override val cardinality: Cardinality
            get() = arr.cardinality

        override fun process(delta: DataDelta) {
            val quad = delta.value
            when (delta) {
                is DataAddition -> {
                    val peeked = peekNewlyReachable(quad)
                    arr.addAll(peeked.map { mappingOf(context, end.id to it) })
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
            return result.map { mappingOf(context, end.id to it) }.toStream()
        }

        override fun peek(deletion: DataDeletion): Stream<Mapping> {
            TODO("Not yet implemented")
        }

        private fun peekNewlyReachable(quad: EncodedQuad): Set<TermIdentifier> {
            val new = getNewSegments(quad)
            // as it's possible for multiple segments to be returned from a single quad insertion, and this in turn
            //  cause some paths to come back in duplicates, we make it instantly distinct
            val result = mutableSetOf<TermIdentifier>()
            segments.newPathsOnAdding(new)
                .forEach {
                    if (it.start != start.id) {
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

        override fun reindex(bindings: BindingIdentifierSet, hint: MappingArrayHint) {
            arr.reindex(bindings, hint)
        }

        override fun toString() = segments.toString()

        private fun getNewSegments(quad: EncodedQuad): Set<SegmentsList.Segment> {
            return inner.peek(DataAddition(quad))
                .mapTo(mutableSetOf()) { SegmentsList.Segment(start = it.get(bridge.id)!!, end = it.get(end.id)!!) }
        }

    }

    class OneOrMoreStatelessExact(
        val context: QueryContext,
        val start: TriplePatternState.Exact,
        val inner: TriplePatternState.StatelessPredicate,
        val end: TriplePatternState.Exact
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

        override fun reindex(bindings: BindingIdentifierSet, hint: MappingArrayHint) {
            // ignored
        }

    }

    class OneOrMoreStatefulExact(
        val context: QueryContext,
        val start: TriplePatternState.Exact,
        inner: TriplePatternState.Predicate,
        val end: TriplePatternState.Exact
    ) : RepeatingPathState() {

        private var satisfied = false

        // "bridge" bindings, responsible for keeping the inner predicate's connection points variable, allowing for
        //  more matches that in turn can produce additional results only obtainable by combining these additional
        //  matches; i.e.
        //  A -> B and B -> C should yield A -> C, which is only possible if we don't enforce an exact match B
        private val intermediateStart = TriplePatternState.Binding(BindingIdentifier(context.newAnonymousBinding()))
        private val intermediateEnd = TriplePatternState.Binding(BindingIdentifier(context.newAnonymousBinding()))
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
                                start = it.get(intermediateStart.id)!!,
                                end = it.get(intermediateEnd.id)!!
                            )
                        }
                    if (!satisfied) {
                        satisfied = segments.newPathsOnAdding(new).any { it.start == start.id && it.end == end.id }
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
                            start = it.get(intermediateStart.id)!!,
                            end = it.get(intermediateEnd.id)!!
                        )
                    }
                if (segments.newPathsOnAdding(new).any { it.start == start.id && it.end == end.id }) {
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

        override fun reindex(bindings: BindingIdentifierSet, hint: MappingArrayHint) {
            // ignored
        }

    }


    abstract val cardinality: Cardinality

    abstract fun process(delta: DataDelta)

    abstract fun peek(addition: DataAddition): Stream<Mapping>

    abstract fun peek(deletion: DataDeletion): Stream<Mapping>

    abstract fun join(mappings: OptimisedStream<Mapping>): Stream<Mapping>

    abstract fun join(mappings: OptimisedStream<Mapping>, ignore: Iterable<Mapping>): Stream<Mapping>

    abstract fun reindex(bindings: BindingIdentifierSet, hint: MappingArrayHint)

    companion object {

        fun zeroOrMore(
            context: QueryContext,
            start: TriplePatternState.Subject,
            predicate: TriplePatternState.ZeroOrMore,
            end: TriplePatternState.Object
        ): RepeatingPathState {
            return when (val inner = predicate.element) {
                is TriplePatternState.StatelessPredicate -> when {
                    start is TriplePatternState.Binding && end is TriplePatternState.Binding ->
                        ZeroOrMoreStatelessBindings(
                            context = context,
                            start = start,
                            inner = inner,
                            end = end,
                        )

                    start is TriplePatternState.Binding && end is TriplePatternState.Exact ->
                        ZeroOrMoreStatelessBindingExact(
                            context = context,
                            start = start,
                            inner = inner,
                            end = end,
                        )

                    start is TriplePatternState.Exact && end is TriplePatternState.Binding ->
                        ZeroOrMoreStatelessExactBinding(
                            context = context,
                            start = start,
                            inner = inner,
                            end = end,
                        )

                    start is TriplePatternState.Exact && end is TriplePatternState.Exact ->
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
                    start is TriplePatternState.Binding && end is TriplePatternState.Binding ->
                        ZeroOrMoreStatefulBindings(
                            context = context,
                            start = start,
                            inner = inner,
                            end = end,
                        )

                    start is TriplePatternState.Binding && end is TriplePatternState.Exact ->
                        ZeroOrMoreStatefulBindingExact(
                            context = context,
                            start = start,
                            inner = inner,
                            end = end,
                        )

                    start is TriplePatternState.Exact && end is TriplePatternState.Binding ->
                        ZeroOrMoreStatefulExactBinding(
                            context = context,
                            start = start,
                            inner = inner,
                            end = end,
                        )

                    start is TriplePatternState.Exact && end is TriplePatternState.Exact ->
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
            start: TriplePatternState.Subject,
            predicate: TriplePatternState.OneOrMore,
            end: TriplePatternState.Object
        ): RepeatingPathState {
            return when (val inner = predicate.element) {
                is TriplePatternState.StatelessPredicate -> when {
                    start is TriplePatternState.Binding && end is TriplePatternState.Binding ->
                        OneOrMoreStatelessBindings(
                            context = context,
                            start = start,
                            inner = inner,
                            end = end,
                        )

                    start is TriplePatternState.Binding && end is TriplePatternState.Exact ->
                        OneOrMoreStatelessBindingExact(
                            context = context,
                            start = start,
                            inner = inner,
                            end = end,
                        )

                    start is TriplePatternState.Exact && end is TriplePatternState.Binding ->
                        OneOrMoreStatelessExactBinding(
                            context = context,
                            start = start,
                            inner = inner,
                            end = end,
                        )

                    start is TriplePatternState.Exact && end is TriplePatternState.Exact ->
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
                    start is TriplePatternState.Binding && end is TriplePatternState.Binding ->
                        OneOrMoreStatefulBindings(
                            context = context,
                            start = start,
                            inner = inner,
                            end = end,
                        )

                    start is TriplePatternState.Binding && end is TriplePatternState.Exact ->
                        OneOrMoreStatefulBindingExact(
                            context = context,
                            start = start,
                            inner = inner,
                            end = end,
                        )

                    start is TriplePatternState.Exact && end is TriplePatternState.Binding ->
                        OneOrMoreStatefulExactBinding(
                            context = context,
                            start = start,
                            inner = inner,
                            end = end,
                        )

                    start is TriplePatternState.Exact && end is TriplePatternState.Exact ->
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

private fun EncodedQuad.toSegment() = SegmentsList.Segment(start = TermIdentifier(s), end = TermIdentifier(o))
