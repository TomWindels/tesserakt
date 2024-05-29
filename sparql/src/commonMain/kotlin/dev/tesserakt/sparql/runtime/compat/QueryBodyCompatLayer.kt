package dev.tesserakt.sparql.runtime.compat

import dev.tesserakt.sparql.compiler.ast.QueryAST
import dev.tesserakt.sparql.compiler.ast.SegmentAST
import dev.tesserakt.sparql.compiler.ast.UnionAST
import dev.tesserakt.sparql.runtime.types.*

class QueryBodyCompatLayer: CompatLayer<QueryAST.QueryBodyAST, QueryASTr.QueryBodyASTr>() {

    override fun convert(source: QueryAST.QueryBodyAST): QueryASTr.QueryBodyASTr {
        val unions = source.unions.convert().toMutableList()
        val patterns = PatternCompatLayer { blocks -> unions.add(blocks.toUnion()) }
            .convert(source.patterns)
        val optional = source.optional.map { OptionalASTr(it.segment.convert()) }
        return QueryASTr.QueryBodyASTr(
            patterns = patterns,
            optional = optional,
            unions = unions
        )
    }

    /**
     * Converts a set of patterns into a union statement, using the input patterns as body patterns
     */
    private fun List<PatternsASTr>.toUnion() = UnionASTr(
        map { patterns ->
            SegmentASTr.Statements(
                statements = QueryASTr.QueryBodyASTr(
                    patterns = patterns,
                    unions = emptyList(),
                    optional = emptyList()
                )
            )
        }
    )

    private fun Iterable<UnionAST>.convert() =
        map { block -> UnionASTr(block.map { segment -> segment.convert() }) }

    private fun SegmentAST.convert(): SegmentASTr = when (this) {
        is SegmentAST.SelectQuery ->
            SegmentASTr.SelectQuery(SelectQueryCompatLayer().convert(query))

        is SegmentAST.Statements ->
            SegmentASTr.Statements(QueryBodyCompatLayer().convert(statements))
    }

}
