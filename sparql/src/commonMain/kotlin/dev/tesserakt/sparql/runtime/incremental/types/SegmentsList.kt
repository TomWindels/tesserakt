package dev.tesserakt.sparql.runtime.incremental.types

import dev.tesserakt.rdf.types.Quad

internal class SegmentsList {

    data class Segment(
        val start: Quad.Term,
        val end: Quad.Term
    ) {
        override fun toString() = "$start -> $end"
    }

    // segments connecting the nodes from above
    private val segments = mutableSetOf<Segment>()
    // all paths that can be formed using the segments from above
    private val paths = mutableSetOf<Segment>()

    /**
     * Calculates all path variations that can be formed when adding the new `segment` to this segment store,
     *  WITHOUT actually adding it. Uses segments to calculate all new variations.
     */
    fun newPathsOnAdding(segment: Segment): Set<Segment> {
        if (segment in segments) {
            // no impact, no new paths could be made
            return emptySet()
        }
        val left = pathsEndingWithUsingSegments(segment, source = segments)
        val right = pathsStartingWithUsingSegments(segment, source = segments)
        // all results from `before` are now connected with those `after`, with the new segment at least being part
        //  of the result
        val result = LinkedHashSet<Segment>((left.size + 1) * (right.size + 1))
        result.add(segment)
        // adding every permutation
        left.forEach { start -> result.add(Segment(start = start, end = segment.end)) }
        right.forEach { end -> result.add(Segment(start = segment.start, end = end)) }
        // adding every in-between path, including those that have start == end, as that is now a valid path
        left.forEach { start -> right.forEach { end -> result.add(Segment(start = start, end = end)) } }
        // existing paths can't result in new sub-results
        return result - paths
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
        val total = this.segments.toMutableSet().also { it.addAll(s) }
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
        return result - paths
    }

    /**
     * Adds the `segment` to the list of segments, allowing it to be used when calculating the delta, and expands
     *  the number of available path variations
     */
    fun insert(segment: Segment) {
        // first calculating & inserting its delta directly from the existing segments
        paths.addAll(newPathsOnAdding(segment))
        // now the segment can be directly added
        segments.add(segment)
    }

    /**
     * Adds the `segment` to the list of segments, allowing it to be used when calculating the delta, and expands
     *  the number of available path variations
     */
    fun insert(segments: Set<Segment>) {
        // first calculating & inserting its delta directly from the existing segments
        paths.addAll(newPathsOnAdding(segments))
        // now the segment can be directly added
        this.segments.addAll(segments)
    }

    fun newReachableStartNodesOnAdding(segment: Segment): Set<Quad.Term> {
        val before = paths.mapTo(mutableSetOf()) { it.start }
        val after = newPathsOnAdding(segment).mapTo(mutableSetOf()) { it.start }
        return after - before
    }

    fun newReachableEndNodesOnAdding(segment: Segment): Set<Quad.Term> {
        val before = paths.mapTo(mutableSetOf()) { it.end }
        val after = newPathsOnAdding(segment).mapTo(mutableSetOf()) { it.end }
        return after - before
    }

    override fun toString() = buildString {
        appendLine("SegmentList [")
        appendLine(" * Segments: $segments")
        appendLine(" * Paths: $paths")
        append("]")
    }

    private fun pathsStartingWithUsingSegments(
        segment: Segment,
        source: Set<Segment>
    ): List<Quad.Term> {
        val result = directlyConnectedEndTermsOf(start = segment.end, source = source)
            .filterTo(mutableListOf()) { it != segment.end }
        // TODO(perf):
        //  juggling between two mutable lists that are flatmapped into with retain methods for optimisation
        var new = result.flatMap { current -> directlyConnectedEndTermsOf(start = current, source = source) }
            .filter { it != segment.end }
        val seen = mutableSetOf<Quad.Term>()
        while (new.isNotEmpty()) {
            result.addAll(new)
            seen.addAll(new)
            new = (new.flatMap { current -> directlyConnectedEndTermsOf(start = current, source = source) } - seen)
                .filter { it != segment.end }
        }
        return result
    }

    private fun pathsEndingWithUsingSegments(
        segment: Segment,
        source: Set<Segment>
    ): List<Quad.Term> {
        val result = directlyConnectedStartTermsOf(end = segment.start, source = source)
            .filterTo(mutableListOf()) { it != segment.start }
        // TODO(perf):
        //  juggling between two mutable lists that are flatmapped into with retain methods for optimisation
        var new = result.flatMap { current -> directlyConnectedStartTermsOf(end = current, source = source) }
            .filter { it != segment.start }
        val seen = mutableSetOf<Quad.Term>()
        while (new.isNotEmpty()) {
            result.addAll(new)
            seen.addAll(new)
            new = (new.flatMap { current -> directlyConnectedStartTermsOf(end = current, source = source) } - seen)
                .filter { it != segment.start }
        }
        return result
    }

    private fun directlyConnectedStartTermsOf(
        end: Quad.Term,
        source: Set<Segment>
    ): List<Quad.Term> =
        source.mapNotNull { segment -> segment.start.takeIf { segment.end == end } }

    private fun directlyConnectedEndTermsOf(
        start: Quad.Term,
        source: Set<Segment>
    ): List<Quad.Term> =
        source.mapNotNull { segment -> segment.end.takeIf { segment.start == start } }

}
