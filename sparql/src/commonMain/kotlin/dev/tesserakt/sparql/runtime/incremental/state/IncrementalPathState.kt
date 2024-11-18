@file:Suppress("NOTHING_TO_INLINE")

package dev.tesserakt.sparql.runtime.incremental.state

import dev.tesserakt.sparql.runtime.common.types.Pattern
import dev.tesserakt.sparql.runtime.common.util.getTermOrNull
import dev.tesserakt.sparql.runtime.core.Mapping
import dev.tesserakt.sparql.runtime.core.mappingOf
import dev.tesserakt.sparql.runtime.core.pattern.bindingName
import dev.tesserakt.sparql.runtime.incremental.types.SegmentsList

internal sealed class IncrementalPathState(
    protected val start: Pattern.Subject,
    protected val end: Pattern.Object
) {

    protected val segments = SegmentsList()
    protected val bs = start.bindingName
    protected val bo = end.bindingName

    protected fun SegmentsList.Segment.toMapping() = mappingOf(bs to start, bo to end)

    /**
     * Denotes the number of matches it contains, useful for quick cardinality calculations (e.g., joining this state
     *  on an empty solution results in [size] results, or a size of 0 guarantees no results will get generated)
     */
    // FIXME needs testing
    val size: Int get() = segments.paths.size

    abstract fun join(mappings: List<Mapping>): List<Mapping>

    abstract fun delta(segment: SegmentsList.Segment): List<Mapping>

    class ZeroOrMore(
        start: Pattern.Subject,
        end: Pattern.Object
    ) : IncrementalPathState(start = start, end = end) {

        override fun join(mappings: List<Mapping>): List<Mapping> = mappings.flatMap { mapping ->
            val start = start.getTermOrNull(mapping)
            val end = end.getTermOrNull(mapping)
            when {
                start != null && end != null -> {
                    if (segments.isConnected(start, end)) listOf(mapping) else emptyList()
                }

                start != null -> {
                    val base = if (bo != null) {
                        // object is bound, so adding it to the mapping
                        segments.allConnectedEndTermsOf(start).map { mapping + (bo to it) }
                    } else {
                        // start was already part of the mapping or not bound, as it is non-null, thus the mapping
                        //  does not have to be altered further
                        segments.allConnectedEndTermsOf(start).map { mapping }
                    }
                    // adding a null-length relation, meaning end == start, if it has already been inserted
                    if (start in segments.nodes) {
                        (mapping + mappingOf(bo to start)) + base
                    } else {
                        base
                    }
                }

                end != null -> {
                    val base = if (bs != null) {
                        segments.allConnectedStartTermsOf(end).map { mapping + (bs to it) }
                    } else {
                        segments.allConnectedStartTermsOf(end).map { mapping }
                    }
                    // adding a null-length relation, meaning end == start, if it has already been inserted
                    if (end in segments.nodes) {
                        (mapping + mappingOf(bs to end)) + base
                    } else {
                        base
                    }
                }

                else -> {
                    segments.paths.map { it.toMapping() + mapping }
                }
            }
        }

        override fun delta(segment: SegmentsList.Segment): List<Mapping> {
            val result = segments.newPathsOnAdding(segment).let { paths ->
                val base = ArrayList<Mapping>(paths.size + 2)
                paths.mapTo(base) { it.toMapping() }
            }
            if (segment.start !in segments.nodes) {
                result.add(
                    SegmentsList.Segment(
                        start = segment.start,
                        end = segment.start
                    ).toMapping()
                )
            }
            if (segment.end !in segments.nodes) {
                result.add(
                    SegmentsList.Segment(
                        start = segment.end,
                        end = segment.end
                    ).toMapping()
                )
            }
            return result
        }

    }

    class OneOrMore(
        start: Pattern.Subject,
        end: Pattern.Object
    ) : IncrementalPathState(start = start, end = end) {

        override fun join(mappings: List<Mapping>): List<Mapping> = mappings.flatMap { mapping ->
            val start = start.getTermOrNull(mapping)
            val end = end.getTermOrNull(mapping)
            when {
                start != null && end != null -> {
                    if (segments.isConnected(start, end)) listOf(mapping) else emptyList()
                }

                start != null -> {
                    if (bo != null) {
                        // object is bound, so adding it to the mapping
                        segments.allConnectedEndTermsOf(start).map { mapping + (bo to it) }
                    } else {
                        // start was already part of the mapping or not bound, as it is non-null, thus the mapping
                        //  does not have to be altered further
                        segments.allConnectedEndTermsOf(start).map { mapping }
                    }
                }

                end != null -> {
                    if (bs != null) {
                        segments.allConnectedStartTermsOf(end).map { mapping + (bs to it) }
                    } else {
                        segments.allConnectedStartTermsOf(end).map { mapping }
                    }
                }

                else -> {
                    segments.paths.map { it.toMapping() + mapping }
                }
            }
        }

        override fun delta(segment: SegmentsList.Segment) = segments.newPathsOnAdding(segment).map { it.toMapping() }

    }

    fun insert(segment: SegmentsList.Segment) = segments.insert(segment)

}

// helper for ZeroOrMore above
private inline operator fun <T> T.plus(collection: Collection<T>): List<T> = ArrayList<T>(collection.size + 1)
    .also { it.add(this); it.addAll(collection) }
