package dev.tesserakt.sparql.runtime.query.jointree

import dev.tesserakt.sparql.QueryStatistics
import dev.tesserakt.sparql.runtime.collection.MappingArrayHint
import dev.tesserakt.sparql.runtime.evaluation.BindingIdentifierSet
import dev.tesserakt.sparql.runtime.evaluation.DataDelta
import dev.tesserakt.sparql.runtime.evaluation.MappingDelta
import dev.tesserakt.sparql.runtime.evaluation.Statistics
import dev.tesserakt.sparql.runtime.evaluation.context.QueryContext
import dev.tesserakt.sparql.runtime.query.MutableJoinState
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

    override val properties: MutableJoinState.Properties
        get() = MutableJoinState.Properties.EMPTY

    override val cardinality: Cardinality
        get() = OneCardinality // always matches

    override fun enqueue(delta: DataDelta) {
        // no-op
    }

    override fun process(): OptimisedStream<MappingDelta> {
        return emptyStream()
    }

    override fun join(delta: MappingDelta): Stream<MappingDelta> {
        return streamOf(delta)
    }

    override fun reindex(bindings: BindingIdentifierSet, hint: MappingArrayHint) {
        // no-op
    }

    override fun stats(context: QueryContext, granularity: QueryStatistics.Granularity): Statistics {
        return Statistics.Empty
    }


    override fun toString(): String = "Empty join tree"

}
