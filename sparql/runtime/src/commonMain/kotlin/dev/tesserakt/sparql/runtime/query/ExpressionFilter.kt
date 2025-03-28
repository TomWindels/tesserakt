package dev.tesserakt.sparql.runtime.query

import dev.tesserakt.sparql.runtime.evaluation.MappingDelta
import dev.tesserakt.sparql.runtime.evaluation.QueryContext
import dev.tesserakt.sparql.runtime.stream.Stream
import dev.tesserakt.sparql.runtime.stream.filtered
import dev.tesserakt.sparql.types.Expression
import kotlin.jvm.JvmInline

@JvmInline
value class ExpressionFilter private constructor(private val compiled: FilterExpression): StatelessFilter {

    constructor(context: QueryContext, expression: Expression) : this(compiled = FilterExpression(context, expression))

    override fun filter(input: Stream<MappingDelta>): Stream<MappingDelta> {
        return input.filtered { compiled.test(it.value) }
    }

}
