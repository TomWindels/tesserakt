package tesserakt.sparql

import tesserakt.sparql.compiler.analyser.QueryProcessor
import tesserakt.sparql.compiler.lexer.StringLexer
import tesserakt.sparql.compiler.types.QueryAST
import tesserakt.sparql.runtime.compat.QueryCompatLayer
import tesserakt.sparql.runtime.query.Query
import tesserakt.sparql.runtime.query.SelectQuery
import tesserakt.sparql.runtime.types.QueryASTr
import tesserakt.sparql.runtime.types.SelectQueryASTr
import tesserakt.sparql.runtime.validator.SelectQueryOutputValidator
import tesserakt.sparql.runtime.validator.Validator.Companion.validate

// `open` as it allows for custom queries with hooks for custom implementations between the input -> ast -> query
//  pipeline
abstract class Compiler {

    abstract fun compile(raw: String): Query<*, *>

    open fun String.toAST(): QueryAST =
        QueryProcessor().process(StringLexer(this))

    open fun QueryASTr.toQuery(): Query<*, *> = when (this) {
        is SelectQueryASTr -> { SelectQuery(this) }
    }

    /* alternative methods/syntax */

    fun String.asSPARQLQuery() = compile(this)

    fun String.asSPARQLSelectQuery() = compile(this) as SelectQuery

    object Default: Compiler() {

        private val validators = listOf(
            SelectQueryOutputValidator,
        )

        override fun compile(raw: String): Query<*, *> {
            // compiling the input query
            val ast = raw.toAST()
            // converting it to a subset supported by the runtime
            val compat = QueryCompatLayer().convert(ast)
            // validating if the resulting
            validators.validate(compat)
            return compat.toQuery()
        }

    }

}
