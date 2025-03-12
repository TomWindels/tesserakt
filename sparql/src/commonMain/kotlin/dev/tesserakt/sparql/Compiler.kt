package dev.tesserakt.sparql

import dev.tesserakt.sparql.compiler.analyser.QueryProcessor
import dev.tesserakt.sparql.compiler.lexer.StringLexer
import dev.tesserakt.sparql.types.QueryStructure
import kotlin.jvm.JvmInline

open class Compiler {

    @JvmInline
    value class CompiledQuery(
        /**
         * The underlying query structure. Accessing this type requires the `:sparql:core` dependency.
         */
        val structure: QueryStructure
    )

    open fun compile(query: String): CompiledQuery {
        return CompiledQuery(QueryProcessor().process(StringLexer(query)))
    }

    fun String.toSparqlSelectQuery() = Query.Select(query = this, compiler = this@Compiler)

}
