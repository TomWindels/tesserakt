package dev.tesserakt.sparql.runtime.query.jointree

import dev.tesserakt.sparql.QueryStatistics
import dev.tesserakt.sparql.runtime.evaluation.DataDelta
import dev.tesserakt.sparql.runtime.evaluation.MappingDelta
import dev.tesserakt.sparql.runtime.evaluation.Statistics
import dev.tesserakt.sparql.runtime.evaluation.context.QueryContext
import dev.tesserakt.sparql.runtime.query.MutableJoinState
import dev.tesserakt.sparql.runtime.stream.OptimisedStream
import dev.tesserakt.sparql.runtime.stream.Stream

/**
 * A general join tree type, containing intermediate joined values depending on the tree implementation
 */
interface JoinTree : MutableJoinState {

    override val properties: MutableJoinState.Properties

    /**
     * Updates the state according to the [delta] change, returning the set of output changes that happened because of
     *  it.
     */
    override fun process(delta: DataDelta): OptimisedStream<MappingDelta>

    /**
     * Returns the result of [join]ing the [delta] with its own internal state
     */
    override fun join(delta: MappingDelta): Stream<MappingDelta>

    override fun stats(context: QueryContext, granularity: QueryStatistics.Granularity): Statistics

    companion object

}
