package dev.tesserakt.sparql.runtime.incremental.compat

import dev.tesserakt.sparql.compiler.ast.QueryAST
import dev.tesserakt.sparql.compiler.ast.SegmentAST
import dev.tesserakt.sparql.compiler.ast.UnionAST
import dev.tesserakt.sparql.runtime.incremental.types.*

object QueryBodyCompatLayer: IncrementalCompatLayer<QueryAST.QueryBodyAST, Query.QueryBody>() {

    override fun convert(source: QueryAST.QueryBodyAST): Query.QueryBody {
        val unions = source.unions.convert().toMutableList()
        val patterns = PatternCompatLayer { blocks -> unions.add(Union(blocks)) }
            .convert(source.patterns)
        val optional = source.optionals.map { Optional(it.segment.convert()) }
        return Query.QueryBody(
            patterns = patterns,
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

}
