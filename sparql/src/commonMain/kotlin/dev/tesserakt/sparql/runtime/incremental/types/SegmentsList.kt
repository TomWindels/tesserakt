package dev.tesserakt.sparql.runtime.incremental.types

import dev.tesserakt.rdf.types.Quad

internal class SegmentsList {

    data class Segment(
        val start: Quad.Term, val end: Quad.Term
    ) {
        override fun toString() = "$start -> $end"
    }

    // segments connecting the nodes from above, count for removal checks
    private val segments = Counter<Segment>()

    // all paths that can be formed using the segments from above
    private val _paths = mutableSetOf<Segment>()
    val paths: Set<Segment> get() = _paths

    /**
     * Calculates all path variations that can be formed when adding the new `segment` to this segment store,
     *  WITHOUT actually adding it. Uses segments to calculate all new variations.
     */
    fun newPathsOnAdding(element: Segment): Set<Segment> {
        if (element in segments) {
            // no impact, no new paths could be made
            return emptySet()
        }
        val result = connect(element, segments.current)
        // existing paths can't result in new sub-results
        result.removeAll(_paths)
        return result
    }

    fun removedPathsOnRemoving(element: Segment): Set<Segment> {
        if (segments[element] != 1) {
            // no impact: the segment is either not present at all (no paths could possibly use it), or the
            //  segment occurs multiple times (so it is not outright removed)
            return emptySet()
        }
        // TODO(perf): this can definitely be improved
        // getting all available remaining paths without this segment
        val remaining = remainingPathsOnRemoving(element)
        // the final result are the currently available paths that aren't available anymore afterwards
        return _paths - remaining
    }

    fun removedPathsOnRemoving(elements: Iterable<Segment>): Set<Segment> {
        // TODO(perf): this can definitely be improved
        // getting all available remaining paths without this segment
        val remaining = remainingPathsOnRemoving(elements)
        // the final result are the currently available paths that aren't available anymore afterwards
        return _paths - remaining
    }

    fun remainingPathsOnRemoving(element: Segment): Set<Segment> {
        if (segments[element] != 1) {
            // no impact: the segment is either not present at all (no paths could possibly use it), or the
            //  segment occurs multiple times (so it is not outright removed)
            return _paths
        }
        // TODO(perf): this can definitely be improved
        // getting all available remaining paths without this segment
        return combine(segments.current - element)
    }

    fun remainingPathsOnRemoving(elements: Iterable<Segment>): Set<Segment> {
        val counts = elements.groupingBy { it }.eachCount()
        val removed = counts.mapNotNullTo(mutableSetOf()) { (segment, count) ->
            segment.takeIf { segments[segment] <= count }
        }
        if (removed.isEmpty()) {
            return _paths
        }
        // TODO(perf): this can definitely be improved
        // getting all available remaining paths without the removed segments
        return combine(segments.current - removed)
    }

    /**
     * Calculates all path variations that can be formed when adding the new `segment` to this segment store,
     *  WITHOUT actually adding it. Uses segments to calculate all new variations.
     */
    fun newPathsOnAdding(segments: Set<Segment>): Set<Segment> {
        val s = segments.filter { it !in this.segments }
        if (s.isEmpty()) {
            // no impact, no new paths could be made
            return emptySet()
        }
        val result = mutableSetOf<Segment>()
        val total = this.segments.current
            // copy
            .toMutableSet().also { it.addAll(s) }
        s.forEach { segment ->
            total.remove(segment)
            val left = pathsEndingWithUsingSegments(segment, source = total)
            val right = pathsStartingWithUsingSegments(segment, source = total)
            total.add(segment)
            // all results from `before` are now connected with those `after`, with the new segment at least being part
            //  of the result
            result.add(segment)
            // adding every permutation
            left.forEach { start -> result.add(Segment(start = start, end = segment.end)) }
            right.forEach { end -> result.add(Segment(start = segment.start, end = end)) }
            // adding every in-between path, including those that have start == end, as that is now a valid path
            left.forEach { start -> right.forEach { end -> result.add(Segment(start = start, end = end)) } }
        }
        // existing paths can't result in new sub-results
        result.removeAll(_paths)
        return result
    }

    /**
     * Adds the `segment` to the list of segments, allowing it to be used when calculating the delta, and expands
     *  the number of available path variations
     */
    fun insert(element: Segment) {
        // first calculating & inserting its delta directly from the existing segments
        _paths.addAll(newPathsOnAdding(element))
        // now the segment's count can be updated
        segments.increment(element)
    }

    /**
     * Adds the `segment` to the list of segments, allowing it to be used when calculating the delta, and expands
     *  the number of available path variations
     */
    fun insert(elements: Iterable<Segment>) {
        // first calculating & inserting its delta directly from the existing segments; they can be distinct (= set)
        //  for the new path calculation
        _paths.addAll(newPathsOnAdding(elements.toSet()))
        // now the segment can be directly added, based on their # of occurrences
        segments.increment(elements.groupingBy { it }.eachCount())
    }

    fun remove(element: Segment) {
        when (val count = segments[element]) {
            0 -> {
                // not found, ignoring
            }
            1 -> {
                // directly removing the segment
                segments.decrement(element)
                // available paths have changed
                // TODO(perf): this can definitely be improved
                // getting all available remaining paths without this segment
                _paths.clear()
                _paths.addAll(combine(segments.current))
            }
            else -> {
                // only subtracting one, but available paths haven't altered
                segments.decrement(element)
            }
        }
    }

    fun remove(elements: Iterable<Segment>) {
        val counts = elements.groupingBy { it }.eachCount()
        val removed = counts.mapNotNullTo(mutableSetOf()) { (segment, count) ->
            segment.takeIf { val total = segments[segment]; total <= count }
        }
        // updating all segment information
        counts.forEach { (element, count) ->
            segments.decrement(element, count)
        }
        if (removed.isEmpty()) {
            // nothing to remove, paths haven't altered, so not rebuilding
            return
        }
        // rebuilding the path structure as a result
        // TODO(perf): this can possibly be improved, depending on whether a path rebuild or a remove check is faster
        // getting all available remaining paths without this segment
        _paths.clear()
        _paths.addAll(combine(segments.current))
    }

    // TODO remove
    fun newReachableStartNodesOnAdding(segment: Segment): Set<Quad.Term> {
        val before = _paths.mapTo(mutableSetOf()) { it.start }
        val after = newPathsOnAdding(segment).mapTo(mutableSetOf()) { it.start }
        return after - before
    }

    // TODO remove
    fun newReachableEndNodesOnAdding(segment: Segment): Set<Quad.Term> {
        val before = _paths.mapTo(mutableSetOf()) { it.end }
        val after = newPathsOnAdding(segment).mapTo(mutableSetOf()) { it.end }
        return after - before
    }

    override fun toString() = buildString {
        appendLine("SegmentList [")
        appendLine(" * Segments: ${segments.asIterable().joinToString { "<${it.key}>: ${it.value}" }}")
        appendLine(" * Paths: $_paths")
        append("]")
    }

    companion object {

        /**
         * Returns all paths that can be formed when connecting [segment] with the other [available] segments
         */
        private fun connect(segment: Segment, available: Set<Segment>): LinkedHashSet<Segment> {
            if (segment.start == segment.end) {
                return linkedSetOf()
            }
            val left = pathsEndingWithUsingSegments(segment, source = available)
            val right = pathsStartingWithUsingSegments(segment, source = available)
            // all results from `before` are now connected with those `after`, with the new segment at least being part
            //  of the result
            val result = LinkedHashSet<Segment>((left.size + 1) * (right.size + 1))
            result.add(segment)
            // adding every permutation
            left.forEach { start -> if (start != segment.end) result.add(Segment(start = start, end = segment.end)) }
            right.forEach { end -> if (end != segment.start) result.add(Segment(start = segment.start, end = end)) }
            // adding every in-between path, including those that have start == end, as that is now a valid path
            left.forEach { start -> right.forEach { end -> if (start != end) result.add(Segment(start = start, end = end)) } }
            return result
        }

        /**
         * Returns all paths that can be formed using the provided [segments]
         *
         * This is a "stateless" combine operation; doesn't take existing paths into account
         */
        private fun combine(segments: Set<Segment>): LinkedHashSet<Segment> {
            // TODO(perf): this can definitely be improved upon
            val result = LinkedHashSet<Segment>(segments.size * segments.size)
            val s = segments.toMutableSet()
            segments.forEach { segment ->
                s.remove(segment)
                result.addAll(connect(segment, s))
                s.add(segment)
            }
            return result
        }

        private fun pathsStartingWithUsingSegments(
            segment: Segment, source: Set<Segment>
        ): Set<Quad.Term> {
            val result = directlyConnectedEndTermsOf(
                start = segment.end,
                source = source
            ).filterTo(mutableSetOf()) { it != segment.end }
            // TODO(perf):
            //  juggling between two mutable lists that are flatmapped into with retain methods for optimisation
            var new = result
                .flatMap { current -> directlyConnectedEndTermsOf(start = current, source = source) }
                .filter { it != segment.end }
            val seen = mutableSetOf(segment.end)
            while (new.isNotEmpty()) {
                result.addAll(new)
                seen.addAll(new)
                new = (new.flatMap { current ->
                    directlyConnectedEndTermsOf(
                        start = current,
                        source = source
                    )
                } - seen).filter { it != segment.end }
            }
            return result
        }

        private fun pathsEndingWithUsingSegments(
            segment: Segment, source: Set<Segment>
        ): Set<Quad.Term> {
            val result = directlyConnectedStartTermsOf(
                end = segment.start,
                source = source
            ).filterTo(mutableSetOf()) { it != segment.start }
            // TODO(perf):
            //  juggling between two mutable lists that are flatmapped into with retain methods for optimisation
            var new = result
                .flatMap { current -> directlyConnectedStartTermsOf(end = current, source = source) }
                .filter { it != segment.start }
            val seen = mutableSetOf(segment.start)
            while (new.isNotEmpty()) {
                result.addAll(new)
                seen.addAll(new)
                new = (new.flatMap { current ->
                    directlyConnectedStartTermsOf(
                        end = current,
                        source = source
                    )
                } - seen).filter { it != segment.start }
            }
            return result
        }

        private fun directlyConnectedStartTermsOf(
            end: Quad.Term, source: Set<Segment>
        ): List<Quad.Term> = source.mapNotNull { segment -> segment.start.takeIf { segment.end == end } }

        private fun directlyConnectedEndTermsOf(
            start: Quad.Term, source: Set<Segment>
        ): List<Quad.Term> = source.mapNotNull { segment -> segment.end.takeIf { segment.start == start } }

    }

}
