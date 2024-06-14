package dev.tesserakt.sparql.runtime.incremental.compat

import dev.tesserakt.sparql.compiler.ast.*
import dev.tesserakt.sparql.runtime.common.compat.ExpressionCompatLayer
import dev.tesserakt.sparql.runtime.common.types.Expression
import dev.tesserakt.sparql.runtime.incremental.types.*

object QueryBodyCompatLayer: IncrementalCompatLayer<QueryAST.QueryBodyAST, Query.QueryBody>() {

    override fun convert(source: QueryAST.QueryBodyAST): Query.QueryBody {
        val unions = source.unions.convert().toMutableList()
        val patterns = PatternCompatLayer { blocks -> unions.add(Union(blocks)) }
            .convert(source.patterns)
        val optional = source.optionals.map { Optional(it.segment.convert()) }
        val filters = source.filters.map { it.convert() }
        val bindingStatements = source.bindingStatements.map { it.convert() }
        return Query.QueryBody(
            patterns = patterns,
            filters = filters,
            bindingStatements = bindingStatements,
            optional = optional,
            unions = unions
        )
    }

    private fun Iterable<UnionAST>.convert() =
        map { block -> Union(block.map { segment -> segment.convert() }) }

    private fun SegmentAST.convert(): Segment = when (this) {
        is SegmentAST.SelectQuery ->
            SelectQuerySegment(SelectQueryCompatLayer.convert(query))

        is SegmentAST.Statements ->
            StatementsSegment(convert(statements))
    }

    private fun FilterAST.convert(): Filter = when (this) {
        is FilterAST.Predicate -> Filter(Expression.convert(expression))
        is FilterAST.Regex -> Filter(Expression.convert(this))
    }

    private fun ExpressionAST.BindingStatement.convert() = BindingStatement(expression = ExpressionCompatLayer.convert(expression), target = target)

}
