package dev.tesserakt.sparql.runtime.query.jointree

import dev.tesserakt.sparql.runtime.evaluation.DataDelta
import dev.tesserakt.sparql.runtime.evaluation.MappingDelta
import dev.tesserakt.sparql.runtime.stream.OptimisedStream
import dev.tesserakt.sparql.runtime.stream.Stream
import dev.tesserakt.sparql.runtime.stream.emptyStream
import dev.tesserakt.sparql.runtime.stream.streamOf
import dev.tesserakt.sparql.util.Cardinality
import dev.tesserakt.sparql.util.OneCardinality

/**
 * An empty join tree; a simple implementation of [JoinTree] that contains no inner state of its own
 */
data object EmptyJoinTree: JoinTree {

    override val bindings: Set<String>
        get() = emptySet()

    override val cardinality: Cardinality
        get() = OneCardinality // always matches

    override fun peek(delta: DataDelta): OptimisedStream<MappingDelta> {
        return emptyStream()
    }

    override fun process(delta: DataDelta) {
        // nothing to do
    }

    override fun join(delta: MappingDelta): Stream<MappingDelta> {
        return streamOf(delta)
    }

    override fun toString(): String = "Empty join tree"

}
