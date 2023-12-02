package core.rdf

import core.sparql.compiler.analyser.QueryProcessor
import core.sparql.compiler.lexer.StringLexer
import core.sparql.compiler.types.QueryAST

class SPARQL {

    companion object {

        fun parse(raw: String) {
            process(raw)
            // chain it with the optimiser and a final usable query type (that can employ planning on data sources)
        }

        fun process(raw: String): QueryAST {
            return QueryProcessor().process(StringLexer(raw))
        }

    }

}
