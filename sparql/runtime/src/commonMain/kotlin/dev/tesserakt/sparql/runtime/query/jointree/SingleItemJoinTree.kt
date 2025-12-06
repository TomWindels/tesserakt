package dev.tesserakt.sparql.runtime.query.jointree

import dev.tesserakt.sparql.runtime.collection.MappingArrayHint
import dev.tesserakt.sparql.runtime.evaluation.BindingIdentifierSet
import dev.tesserakt.sparql.runtime.evaluation.DataDelta
import dev.tesserakt.sparql.runtime.evaluation.MappingDelta
import dev.tesserakt.sparql.runtime.evaluation.context.QueryContext
import dev.tesserakt.sparql.runtime.query.MutableJoinState
import dev.tesserakt.sparql.runtime.query.TriplePatternState
import dev.tesserakt.sparql.runtime.query.UnionState
import dev.tesserakt.sparql.runtime.stream.OptimisedStream
import dev.tesserakt.sparql.runtime.stream.Stream
import dev.tesserakt.sparql.types.TriplePattern
import dev.tesserakt.sparql.types.Union
import dev.tesserakt.sparql.util.Cardinality
import kotlin.jvm.JvmInline
import kotlin.jvm.JvmName

@JvmInline
value class SingleItemJoinTree<J: MutableJoinState>(private val element: J): JoinTree {

    override val bindings: Set<String>
        get() = element.bindings

    override val cardinality: Cardinality
        get() = element.cardinality

    init {
        // the internal element should have no indexes as we don't have joining with other elements
        element.reindex(BindingIdentifierSet.EMPTY, MappingArrayHint.DEFAULT)
    }

    override fun peek(delta: DataDelta): OptimisedStream<MappingDelta> {
        return element.peek(delta)
    }

    override fun process(delta: DataDelta) {
        element.process(delta)
    }

    override fun join(delta: MappingDelta): Stream<MappingDelta> {
        return element.join(delta)
    }

    override fun debugInformation(): String = buildString {
        appendLine(" * Join tree statistics (SingleItem)")
        appendLine("\t $element")
    }

    override fun reindex(
        bindings: BindingIdentifierSet,
        hint: MappingArrayHint
    ) {
        // we can pass this to the inner element directly; it's part of a larger structure
        //  that can benefit from specific indexes
        element.reindex(bindings, hint)
    }

    companion object {

        @JvmName("forPatterns")
        operator fun invoke(context: QueryContext, patterns: List<TriplePattern>) = SingleItemJoinTree(
            element = patterns.single().let { TriplePatternState.from(context, it) }
        )

        @JvmName("forUnions")
        operator fun invoke(context: QueryContext, unions: List<Union>) = SingleItemJoinTree(
            element = unions.single().let { UnionState(context, it) }
        )

    }

}
