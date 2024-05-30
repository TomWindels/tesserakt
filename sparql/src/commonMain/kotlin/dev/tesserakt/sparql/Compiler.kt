package dev.tesserakt.sparql

import dev.tesserakt.sparql.compiler.analyser.QueryProcessor
import dev.tesserakt.sparql.compiler.ast.QueryAST
import dev.tesserakt.sparql.compiler.lexer.StringLexer
import dev.tesserakt.sparql.runtime.incremental.compat.QueryCompatLayer
import dev.tesserakt.sparql.runtime.incremental.query.IncrementalQuery
import dev.tesserakt.sparql.runtime.incremental.query.IncrementalSelectQuery
import dev.tesserakt.sparql.runtime.incremental.types.Query
import dev.tesserakt.sparql.runtime.incremental.types.SelectQuery
import dev.tesserakt.sparql.runtime.incremental.validation.Validator
import dev.tesserakt.sparql.runtime.incremental.validation.Validator.Companion.validate

// `open` as it allows for custom queries with hooks for custom implementations between
//  the input -> ast -> executable (continuous / incremental) query pipeline
abstract class Compiler {

    abstract fun compile(raw: String): IncrementalQuery<*, *>

    open fun String.toAST(): QueryAST =
        QueryProcessor().process(StringLexer(this))

    open fun Query.toIncrementalQuery(): IncrementalQuery<*, *> = when (this) {
        is SelectQuery -> { IncrementalSelectQuery(this) }
    }

    /* alternative methods/syntax */

    fun String.asSPARQLQuery() = compile(this)

    fun String.asSPARQLSelectQuery() = compile(this) as IncrementalSelectQuery

    object Default: Compiler() {

        private val validators = emptyList<Validator<*>>()

        override fun compile(raw: String): IncrementalQuery<*, *> {
            // compiling the input query
            val ast = raw.toAST()
            // converting it to a subset supported by the runtime
            val compat = QueryCompatLayer().convert(ast)
            // validating if the resulting
            validators.validate(compat)
            return compat.toIncrementalQuery()
        }

    }

}
