package dev.tesserakt.sparql.runtime.query.select

import dev.tesserakt.sparql.runtime.evaluation.mapping.Mapping

class ConfinedOutputState(
    /**
     * The unconfined inner state, responsible for tracking the entire output of the owning query state.
     * All output is always captured, as data changes (data deletions, and depending on the query, also data insertions)
     *  can cause outputs to be removed during ongoing evaluation.
     */
    private val inner: OutputState.Unconfined,
    private val offset: Int,
    private val limit: Int,
): OutputState {

    init {
        require(offset != 0 || limit != Int.MAX_VALUE) {
            "A confined output state was created with no non-default OFFSET or LIMIT values"
        }
        require(limit >= 0) {
            "Limit is not allowed to be negative, but is $limit"
        }
        require(offset >= 0) {
            "Offset is not allowed to be negative, but is $offset"
        }
    }

    override val size: Int
        get() = (inner.size - offset).coerceIn(0, limit)

    override fun isEmpty(): Boolean {
        return size == 0
    }

    override fun onResultAdded(result: Mapping) {
        inner.onResultAdded(result)
    }

    override fun onResultRemoved(result: Mapping) {
        inner.onResultRemoved(result)
    }

    override fun iterator(): Iterator<Mapping> = object: Iterator<Mapping> {

        private val iter = inner.iterator()
            // skipping `offset` elements, as the output's order most likely matters
            .also { i -> repeat(offset.coerceAtMost(inner.size)) { i.next() } }
        private var i = 0

        override fun hasNext(): Boolean {
            // iter should have next, as `this.size` is influenced by the inner state's size
            return i < size
        }

        override fun next(): Mapping {
            ++i
            return iter.next()
        }
    }

    override fun contains(element: Mapping): Boolean {
        forEach {
            if (it == element) return true
        }
        return false
    }

    override fun containsAll(elements: Collection<Mapping>) = throw UnsupportedOperationException()
}
