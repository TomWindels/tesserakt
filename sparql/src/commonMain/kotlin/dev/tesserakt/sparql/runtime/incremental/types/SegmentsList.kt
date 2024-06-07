package dev.tesserakt.sparql.runtime.incremental.types

import dev.tesserakt.rdf.types.Quad

internal class SegmentsList {

    data class Segment(
        val start: Quad.Term,
        val end: Quad.Term
    ) {
        override fun toString() = "$start -> $end"
    }

    private val _segments = mutableListOf<Segment>()
    val segments: List<Segment> get() = _segments
    private val _paths = mutableListOf<Segment>()
    val paths: List<Segment> get() = _paths

    /**
     * Adds the `segment` to the list of segments, allowing it to be used when calculating the delta, and expands
     *  the number of available path variations
     */
    fun insert(segment: Segment) {
        // first calculating & inserting its delta directly from the existing segments
        _paths.addAll(newPathsOnAdding(segment))
        // now the segment can be directly added
        _segments.add(segment)
    }

    /**
     * Calculates all path variations that can be formed when adding the new `segment` to this segment store,
     *  WITHOUT actually adding it. Uses segments to calculate all new variations.
     */
    fun newPathsOnAdding(segment: Segment): List<Segment> {
        val left = pathsEndingWithUsingSegments(end = segment.start)
        val right = pathsStartingWithUsingSegments(start = segment.end)
        // all results from `before` are now connected with those `after`, with the new segment at least being part
        //  of the result
        val result = ArrayList<Segment>((left.size + 1) * (right.size + 1))
        result.add(segment)
        // adding every permutation
        left.forEach { start -> result.add(Segment(start = start, end = segment.end)) }
        right.forEach { end -> result.add(Segment(start = segment.start, end = end)) }
        left.forEach { start -> right.forEach { end -> result.add(Segment(start = start, end = end)) } }
        return result
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

    /**
     * Counts the number of path variations are currently available connecting `start` and `end`
     */
    fun nVariationsBetween(start: Quad.Term, end: Quad.Term): Int =
        _paths.count { it.start == start && it.end == end }

    /**
     * Returns all points that can be formed by beginning with `start`: segments AB, BC, AD, BD and argument A yield
     *  the terms B, C, D, D
     */
    private fun pathsStartingWithUsingSegments(start: Quad.Term): List<Quad.Term> {
        val result = directlyConnectedEndTermsOf(start = start).toMutableList()
        var new = result.flatMap { current -> directlyConnectedEndTermsOf(start = current) }
        while (new.isNotEmpty()) {
            result.addAll(new)
            new = new.flatMap { current -> directlyConnectedEndTermsOf(start = current) }
        }
        return result
    }

    /**
     * Returns all paths that can be formed by ending with `end`: segments AB, BC, AD and argument C yield
     *  the terms B, A
     */
    private fun pathsEndingWithUsingSegments(end: Quad.Term): List<Quad.Term> {
        val result = directlyConnectedStartTermsOf(end = end).toMutableList()
        var new = result.flatMap { current -> directlyConnectedStartTermsOf(end = current) }
        while (new.isNotEmpty()) {
            result.addAll(new)
            new = new.flatMap { current -> directlyConnectedStartTermsOf(end = current) }
        }
        return result
    }

    private fun directlyConnectedStartTermsOf(end: Quad.Term): List<Quad.Term> =
        _segments.mapNotNull { segment -> segment.start.takeIf { segment.end == end } }

    private fun directlyConnectedEndTermsOf(start: Quad.Term): List<Quad.Term> =
        _segments.mapNotNull { segment -> segment.end.takeIf { segment.start == start } }

}
