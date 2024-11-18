@file:Suppress("NOTHING_TO_INLINE")

package dev.tesserakt.sparql.runtime.incremental.state

import dev.tesserakt.sparql.runtime.common.types.Pattern
import dev.tesserakt.sparql.runtime.core.Mapping
import dev.tesserakt.sparql.runtime.core.mappingOf
import dev.tesserakt.sparql.runtime.core.pattern.bindingName
import dev.tesserakt.sparql.runtime.incremental.types.SegmentsList

internal sealed class IncrementalPathState(
    protected val start: Pattern.Subject,
    protected val end: Pattern.Object
) {

    protected val segments = SegmentsList()
    private val bs = start.bindingName
    private val bo = end.bindingName

    protected fun SegmentsList.Segment.toMapping() = mappingOf(bs to start, bo to end)

    class ZeroOrMore(
        start: Pattern.Subject,
        end: Pattern.Object
    ) : IncrementalPathState(start = start, end = end) {

        override fun insert(segment: SegmentsList.Segment): List<Mapping> {
            val result = segments.newPathsOnAdding(segment).toMutableList()
            // only accepting all paths that adhere to the constraints above: if one represents a binding, it gets
            //  added to the resulting mappings; else, it gets filtered out if it doesn't match in value
            // FIXME can be done directly in segment list for efficiency
            if (start is Pattern.Exact) {
                result.removeAll { it.start != start.term }
            }
            if (end is Pattern.Exact) {
                result.removeAll { it.end != end.term }
            }
            if (segment.start !in segments.nodes) {
                result += SegmentsList.Segment(start = segment.start, end = segment.start)
            }
            if (segment.start != segment.end && segment.end !in segments.nodes) {
                result += SegmentsList.Segment(start = segment.end, end = segment.end)
            }
            val delta = result.map { it.toMapping() }.distinct()
            segments.insert(segment)
            return delta
        }

    }

    class OneOrMore(
        start: Pattern.Subject,
        end: Pattern.Object
    ) : IncrementalPathState(start = start, end = end) {

        override fun insert(segment: SegmentsList.Segment): List<Mapping> {
            val result = segments.newPathsOnAdding(segment).toMutableList()
            // only accepting all paths that adhere to the constraints above: if one represents a binding, it gets
            //  added to the resulting mappings; else, it gets filtered out if it doesn't match in value
            // FIXME can be done directly in segment list for efficiency
            if (start is Pattern.Exact) {
                result.removeAll { it.start != start.term }
            }
            if (end is Pattern.Exact) {
                result.removeAll { it.end != end.term }
            }
            val delta = result.map { it.toMapping() }.distinct()
            segments.insert(segment)
            return delta
        }

    }

    abstract fun insert(segment: SegmentsList.Segment): List<Mapping>

    final override fun toString() = "${this@IncrementalPathState::class.simpleName} - $segments"

}

// helper for ZeroOrMore above
private inline operator fun <T> T.plus(collection: Collection<T>): List<T> = ArrayList<T>(collection.size + 1)
    .also { it.add(this); it.addAll(collection) }
