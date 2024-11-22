package dev.tesserakt.sparql.runtime.incremental.types

import dev.tesserakt.rdf.types.Quad

internal sealed class SegmentsList {

    data class Segment(
        val start: Quad.Term,
        val end: Quad.Term
    ) {
        override fun toString() = "$start -> $end"
    }

    // the individual already-visited nodes
    protected val nodes = mutableSetOf<Quad.Term>()
    // segments connecting the nodes from above
    protected val segments = mutableSetOf<Segment>()
    // all paths that can be formed using the segments from above
    protected val paths = mutableSetOf<Segment>()

    /**
     * A segment list implementation that accepts "zero-length" segments as valid paths, creating them whenever a
     *  new segment node is added
     */
    class ZeroLength: SegmentsList() {

        override fun newPathsOnAdding(segment: Segment): Set<Segment> {
            if (segment in segments) {
                // no impact, no new paths could be made
                return emptySet()
            }
            val left = pathsEndingWithUsingSegments(segment)
            val right = pathsStartingWithUsingSegments(segment)
            // all results from `before` are now connected with those `after`, with the new segment at least being part
            //  of the result
            val result = LinkedHashSet<Segment>((left.size + 1) * (right.size + 1))
            result.add(segment)
            // adding every permutation
            left.forEach { start -> result.add(Segment(start = start, end = segment.end)) }
            right.forEach { end -> result.add(Segment(start = segment.start, end = end)) }
            // adding every in-between path that isn't a "zero-length" path (which can occur in circular graphs)
            left.forEach { start -> right.forEach { end -> if (start != end) result.add(Segment(start = start, end = end)) } }
            // if any of the nodes of the new segment are new, they should be considered as zero length paths of their own
            if (segment.start !in nodes) {
                result.add(Segment(start = segment.start, end = segment.start))
            }
            if (segment.end !in nodes) {
                result.add(Segment(start = segment.end, end = segment.end))
            }
            // existing paths can't result in new sub-results
            return result - paths
        }

    }

    /**
     * A segment list implementation that does not accept "zero-length" segments as valid paths, resulting in nodes
     *  only considered as connected upon full path connection
     */
    class SingleLength: SegmentsList() {

        override fun newPathsOnAdding(segment: Segment): Set<Segment> {
            if (segment in segments) {
                // no impact, no new paths could be made
                return emptySet()
            }
            val left = pathsEndingWithUsingSegments(segment)
            val right = pathsStartingWithUsingSegments(segment)
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
        // inserting the nodes too
        nodes.add(segment.start)
        nodes.add(segment.end)
    }

    /**
     * Calculates all path variations that can be formed when adding the new `segment` to this segment store,
     *  WITHOUT actually adding it. Uses segments to calculate all new variations.
     */
    abstract fun newPathsOnAdding(segment: Segment): Set<Segment>

    fun newReachableStartNodesOnAdding(segment: Segment): Set<Quad.Term> {
        val before = paths.map { it.start }.toSet()
        val after = (paths + newPathsOnAdding(segment)).map { it.start }.toSet()
        return after - before
    }

    fun newReachableEndNodesOnAdding(segment: Segment): Set<Quad.Term> {
        val before = paths.map { it.end }.toSet()
        val after = (paths + newPathsOnAdding(segment)).map { it.end }.toSet()
        return after - before
    }

    override fun toString() = buildString {
        appendLine("SegmentList [")
        appendLine(" * Nodes: $nodes")
        appendLine(" * Segments: $segments")
        appendLine(" * Paths: $paths")
        append("]")
    }

    protected fun pathsStartingWithUsingSegments(segment: Segment): List<Quad.Term> {
        val result = directlyConnectedEndTermsOf(start = segment.end)
            .filterTo(mutableListOf()) { it != segment.end }
        // TODO(perf):
        //  juggling between two mutable lists that are flatmapped into with retain methods for optimisation
        var new = result.flatMap { current -> directlyConnectedEndTermsOf(start = current) }
            .filter { it != segment.end }
        val seen = mutableSetOf<Quad.Term>()
        while (new.isNotEmpty()) {
            result.addAll(new)
            seen.addAll(new)
            new = (new.flatMap { current -> directlyConnectedEndTermsOf(start = current) } - seen)
                .filter { it != segment.end }
        }
        return result
    }

    protected fun pathsEndingWithUsingSegments(segment: Segment): List<Quad.Term> {
        val result = directlyConnectedStartTermsOf(end = segment.start)
            .filterTo(mutableListOf()) { it != segment.start }
        // TODO(perf):
        //  juggling between two mutable lists that are flatmapped into with retain methods for optimisation
        var new = result.flatMap { current -> directlyConnectedStartTermsOf(end = current) }
            .filter { it != segment.start }
        val seen = mutableSetOf<Quad.Term>()
        while (new.isNotEmpty()) {
            result.addAll(new)
            seen.addAll(new)
            new = (new.flatMap { current -> directlyConnectedStartTermsOf(end = current) } - seen)
                .filter { it != segment.start }
        }
        return result
    }

    private fun directlyConnectedStartTermsOf(end: Quad.Term): List<Quad.Term> =
        segments.mapNotNull { segment -> segment.start.takeIf { segment.end == end } }

    private fun directlyConnectedEndTermsOf(start: Quad.Term): List<Quad.Term> =
        segments.mapNotNull { segment -> segment.end.takeIf { segment.start == start } }

}
