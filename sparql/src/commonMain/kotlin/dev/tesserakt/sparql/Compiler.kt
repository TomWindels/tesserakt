package dev.tesserakt.sparql

import dev.tesserakt.sparql.ast.CompiledQuery
import dev.tesserakt.sparql.ast.CompiledSelectQuery
import dev.tesserakt.sparql.compiler.analyser.QueryProcessor
import dev.tesserakt.sparql.compiler.lexer.StringLexer
import dev.tesserakt.sparql.runtime.query.QueryState
import dev.tesserakt.sparql.runtime.query.SelectQueryState

// `open` as it allows for custom queries with hooks for custom implementations between
//  the input -> ast -> executable (continuous / incremental) query pipeline
abstract class Compiler {

    abstract fun compile(raw: String): QueryState<*, *>

    open fun String.toAST() =
        QueryProcessor().process(StringLexer(this))

    open fun CompiledQuery.createState(): QueryState<*, *> = when (this) {
        is CompiledSelectQuery -> {
            SelectQueryState(this)
        }
    }

    object Default: Compiler() {

        override fun compile(raw: String): QueryState<*, *> {
            // compiling the input query
            val ast = raw.toAST()
            // using the compiled structure to create a usable state
            return ast.createState()
        }

    }

}
