package dev.tesserakt.sparql.runtime.incremental.types

import dev.tesserakt.rdf.types.Quad

internal class SegmentsList {

    data class Segment(
        val start: Quad.Term,
        val end: Quad.Term
    ) {
        override fun toString() = "$start -> $end"
    }

    // the individual already-visited nodes
    private val _nodes = mutableSetOf<Quad.Term>()
    val nodes: Set<Quad.Term> get() = _nodes
    // segments connecting the nodes from above
    private val _segments = mutableSetOf<Segment>()
    val segments: Set<Segment> get() = _segments
    // all paths that can be formed using the segments from above
    private val _paths = mutableSetOf<Segment>()
    val paths: Set<Segment> get() = _paths

    /**
     * Adds the `segment` to the list of segments, allowing it to be used when calculating the delta, and expands
     *  the number of available path variations
     */
    fun insert(segment: Segment) {
        // first calculating & inserting its delta directly from the existing segments
        _paths.addAll(newPathsOnAdding(segment))
        // now the segment can be directly added
        _segments.add(segment)
        // inserting the nodes too
        _nodes.add(segment.start)
        _nodes.add(segment.end)
    }

    /**
     * Calculates all path variations that can be formed when adding the new `segment` to this segment store,
     *  WITHOUT actually adding it. Uses segments to calculate all new variations.
     */
    fun newPathsOnAdding(segment: Segment): Set<Segment> {
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
        // adding every in-between path
        left.forEach { start -> right.forEach { end -> result.add(Segment(start = start, end = end)) } }
        // existing paths can't result in new sub-results
        return result - _paths
    }

    /**
     * Returns all points that can be formed by beginning with `start`: segments AB, BC, AD, BD and argument A yield
     *  the segments AB, AC, AD, AD
     */
    fun allConnectedEndTermsOf(start: Quad.Term): List<Quad.Term> =
        _paths.mapNotNull { segment -> segment.end.takeIf { segment.start == start } }

    /**
     * Returns all terms that are connected by a path variation ending with `end`: segments AB, BC, AD and argument C yield
     *  the terms B, A
     */
    fun allConnectedStartTermsOf(end: Quad.Term): List<Quad.Term> =
        _paths.mapNotNull { segment -> segment.start.takeIf { segment.end == end } }

    fun isConnected(start: Quad.Term, end: Quad.Term): Boolean =
        _paths.any { it.start == start && it.end == end }

    override fun toString() = buildString {
        appendLine("SegmentList [")
        appendLine(" * Nodes: $nodes")
        appendLine(" * Segments: $segments")
        appendLine(" * Paths: $paths")
        append("]")
    }

    /**
     * Returns all points that can be formed by beginning with `start`: segments AB, BC, AD, BD and argument A yield
     *  the terms B, C, D, D
     */
    private fun pathsStartingWithUsingSegments(segment: Segment): List<Quad.Term> {
        val result = directlyConnectedEndTermsOf(start = segment.end)
            .filter { it != segment.start && it != segment.end }
            .toMutableList()
        var new = result.flatMap { current -> directlyConnectedEndTermsOf(start = current) }
            .filter { it != segment.start && it != segment.end }
        val seen = mutableSetOf<Quad.Term>()
        while (new.isNotEmpty()) {
            result.addAll(new)
            seen.addAll(new)
            new = (new.flatMap { current -> directlyConnectedEndTermsOf(start = current) } - seen)
                .filter { it != segment.start && it != segment.end }
        }
        return result
    }

    /**
     * Returns all paths that can be formed by ending with `end`: segments AB, BC, AD and argument C yield
     *  the terms B, A
     */
    private fun pathsEndingWithUsingSegments(segment: Segment): List<Quad.Term> {
        val result = directlyConnectedStartTermsOf(end = segment.start)
            .filter { it != segment.start && it != segment.end }
            .toMutableList()
        var new = result.flatMap { current -> directlyConnectedStartTermsOf(end = current) }
            .filter { it != segment.start && it != segment.end }
        val seen = mutableSetOf<Quad.Term>()
        while (new.isNotEmpty()) {
            result.addAll(new)
            seen.addAll(new)
            new = (new.flatMap { current -> directlyConnectedStartTermsOf(end = current) } - seen)
                .filter { it != segment.start && it != segment.end }
        }
        return result
    }

    private fun directlyConnectedStartTermsOf(end: Quad.Term): List<Quad.Term> =
        _segments.mapNotNull { segment -> segment.start.takeIf { segment.end == end } }

    private fun directlyConnectedEndTermsOf(start: Quad.Term): List<Quad.Term> =
        _segments.mapNotNull { segment -> segment.end.takeIf { segment.start == start } }

}
