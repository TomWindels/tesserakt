package dev.tesserakt.sparql.runtime.query.jointree

import dev.tesserakt.sparql.QueryStatistics
import dev.tesserakt.sparql.runtime.evaluation.BindingIdentifierSet
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

    override val bindings: BindingIdentifierSet

    /**
     * Returns the [MappingDelta] changes that occur when [process]ing the [delta] in child states part of the tree, without
     *  actually modifying the tree
     */
    override fun peek(delta: DataDelta): OptimisedStream<MappingDelta>

    /**
     * Processes the [delta], updating the tree accordingly
     */
    override fun process(delta: DataDelta)

    /**
     * Returns the result of [join]ing the [delta] with its own internal state
     */
    override fun join(delta: MappingDelta): Stream<MappingDelta>

    override fun stats(context: QueryContext, granularity: QueryStatistics.Granularity): Statistics

    companion object

}
