package tesserakt.rdf

import tesserakt.sparql.compiler.analyser.QueryProcessor
import tesserakt.sparql.compiler.lexer.StringLexer
import tesserakt.sparql.compiler.types.QueryAST

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
