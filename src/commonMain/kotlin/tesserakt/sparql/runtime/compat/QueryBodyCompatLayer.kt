package tesserakt.sparql.runtime.compat

import tesserakt.sparql.compiler.types.QueryAST
import tesserakt.sparql.runtime.types.QueryASTr
import tesserakt.sparql.runtime.types.UnionASTr

class QueryBodyCompatLayer: CompatLayer<QueryAST.QueryBodyAST, QueryASTr.QueryBodyASTr>() {

    override fun convert(source: QueryAST.QueryBodyAST): QueryASTr.QueryBodyASTr {
        // TODO: support pattern compat layer -> union conversion for more complex alternative path support
        return QueryASTr.QueryBodyASTr(
            patterns = PatternCompatLayer().convert(source.patterns),
            optional = source.optional.map { patterns -> PatternCompatLayer().convert(patterns) },
            unions = source.unions.map { UnionASTr(it.map { patterns -> PatternCompatLayer().convert(patterns) }) }
        )
    }

}
