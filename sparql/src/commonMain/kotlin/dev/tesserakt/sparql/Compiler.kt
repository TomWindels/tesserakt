package dev.tesserakt.sparql

import dev.tesserakt.sparql.compiler.analyser.QueryProcessor
import dev.tesserakt.sparql.compiler.lexer.StringLexer
import dev.tesserakt.sparql.types.runtime.query.Query
import dev.tesserakt.sparql.types.runtime.query.SelectQuery

// `open` as it allows for custom queries with hooks for custom implementations between
//  the input -> ast -> executable (continuous / incremental) query pipeline
abstract class Compiler {

    abstract fun compile(raw: String): Query<*, *>

    open fun String.toAST() =
        QueryProcessor().process(StringLexer(this))

    open fun dev.tesserakt.sparql.types.runtime.element.Query.createState(): Query<*, *> = when (this) {
        is dev.tesserakt.sparql.types.runtime.element.SelectQuery -> {
            SelectQuery(this)
        }
    }

    /* alternative methods/syntax */

    fun String.asSPARQLQuery() = compile(this)

    fun String.asSPARQLSelectQuery() = compile(this) as SelectQuery

    object Default: Compiler() {

        override fun compile(raw: String): Query<*, *> {
            // compiling the input query
            val ast = raw.toAST()
            // using the compiled structure to create a usable state
            return ast.createState()
        }

    }

}
