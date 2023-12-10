package tesserakt.sparql

import tesserakt.sparql.compiler.analyser.QueryProcessor
import tesserakt.sparql.compiler.lexer.StringLexer
import tesserakt.sparql.compiler.types.QueryAST
import tesserakt.sparql.compiler.types.SelectQueryAST
import tesserakt.sparql.runtime.query.Query
import tesserakt.sparql.runtime.query.SelectQuery

object SPARQL {

    fun compile(raw: String): Query<*, *> = when (val ast = process(raw)) {
        is SelectQueryAST -> { SelectQuery(ast) }
    }

    fun process(raw: String): QueryAST {
        return QueryProcessor().process(StringLexer(raw))
    }

    /* alternative methods/syntax */

    fun String.asSPARQLQuery() = compile(this)

    fun String.asSPARQLSelectQuery() = compile(this) as SelectQuery

}
