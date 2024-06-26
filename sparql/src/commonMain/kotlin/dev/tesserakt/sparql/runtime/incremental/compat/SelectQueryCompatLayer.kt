package dev.tesserakt.sparql.runtime.incremental.compat

import dev.tesserakt.sparql.compiler.ast.SelectQueryAST
import dev.tesserakt.sparql.runtime.common.compat.ExpressionCompatLayer
import dev.tesserakt.sparql.runtime.incremental.types.SelectQuery

object SelectQueryCompatLayer : IncrementalCompatLayer<SelectQueryAST, SelectQuery>() {

    override fun convert(source: SelectQueryAST): SelectQuery {
        return SelectQuery(
            output = source.output.map { convert(it.value) },
            body = QueryBodyCompatLayer.convert(source.body),
            grouping = source.grouping?.let { ExpressionCompatLayer.convert(it) },
            groupingFilter = source.groupingFilter?.let { ExpressionCompatLayer.convert(it) },
            ordering = source.ordering?.let { ExpressionCompatLayer.convert(it) },
        )
    }

    private fun convert(output: SelectQueryAST.OutputEntry): SelectQuery.Output {
        return when (output) {
            is SelectQueryAST.AggregationOutputEntry ->
                SelectQuery.ExpressionOutput(
                    name = output.aggregation.target.name,
                    expression = ExpressionCompatLayer.convert(output.aggregation.expression)
                )

            is SelectQueryAST.BindingOutputEntry ->
                SelectQuery.BindingOutput(output.binding.name)
        }
    }

}
