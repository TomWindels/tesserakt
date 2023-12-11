package tesserakt.sparql

import tesserakt.sparql.compiler.analyser.QueryProcessor
import tesserakt.sparql.compiler.lexer.StringLexer
import tesserakt.sparql.compiler.types.QueryAST
import tesserakt.sparql.compiler.types.SelectQueryAST
import tesserakt.sparql.compiler.validator.PatternPredicateConstrainedValidator
import tesserakt.sparql.compiler.validator.SelectQueryOutputValidator
import tesserakt.sparql.compiler.validator.Validator.Companion.validate
import tesserakt.sparql.runtime.query.Query
import tesserakt.sparql.runtime.query.SelectQuery

// `open` as it allows for custom queries with hooks for custom implementations between the input -> ast -> query
//  pipeline
abstract class Compiler {

    abstract fun compile(raw: String): Query<*, *>

    open fun String.toAST(): QueryAST =
        QueryProcessor().process(StringLexer(this))

    open fun QueryAST.toQuery(): Query<*, *> = when (this) {
        is SelectQueryAST -> { SelectQuery(this) }
    }

    /* alternative methods/syntax */

    fun String.asSPARQLQuery() = compile(this)

    fun String.asSPARQLSelectQuery() = compile(this) as SelectQuery

    object Default: Compiler() {

        private val validators = listOf(
            SelectQueryOutputValidator,
            PatternPredicateConstrainedValidator,
        )

        override fun compile(raw: String): Query<*, *> {
            val ast = raw.toAST()
            validators.validate(ast)
            return ast.toQuery()
        }

    }

}
