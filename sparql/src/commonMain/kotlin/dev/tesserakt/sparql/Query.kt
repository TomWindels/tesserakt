package dev.tesserakt.sparql

import dev.tesserakt.rdf.types.Store
import dev.tesserakt.sparql.runtime.QueryState
import dev.tesserakt.sparql.runtime.query.QueryState
import dev.tesserakt.sparql.types.QueryStructure
import dev.tesserakt.sparql.types.SelectQueryStructure
import kotlin.jvm.JvmInline

// this is a value class, as we want to identify equal `compiled` query structures
@JvmInline
value class Query<T> private constructor(internal val compiled: QueryStructure) {

    @Suppress("UNCHECKED_CAST")
    internal fun createState(
        source: Store?,
    ): QueryState<T, *> = QueryState(
        structure = compiled,
        source = source,
    ) as QueryState<T, *>

    companion object {

        fun Select(query: String, compiler: Compiler = Compiler()) = Query<Bindings>(
            compiled = with(compiler) {
                val structure = compiler.compile(query).structure
                structure as? SelectQueryStructure
                    ?: throw IllegalArgumentException("Invalid query provided: expected a select query, but got type `${structure::class.simpleName}` instead!")
            }
        )

    }

}
