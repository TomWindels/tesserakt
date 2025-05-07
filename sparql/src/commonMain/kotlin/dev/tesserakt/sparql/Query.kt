package dev.tesserakt.sparql

import dev.tesserakt.sparql.runtime.createState
import dev.tesserakt.sparql.runtime.query.QueryState
import dev.tesserakt.sparql.types.QueryStructure
import dev.tesserakt.sparql.types.SelectQueryStructure

class Query<T> private constructor(val ast: QueryStructure) {

    @Suppress("UNCHECKED_CAST")
    internal fun createState(): QueryState<T, *> = ast.createState() as QueryState<T, *>

    companion object {

        fun Select(query: String, compiler: Compiler = Compiler()) = Query<Bindings>(
            ast = with(compiler) {
                val structure = compiler.compile(query).structure
                structure as? SelectQueryStructure
                    ?: throw IllegalArgumentException("Invalid query provided: expected a select query, but got type `${structure::class.simpleName}` instead!")
            }
        )

    }

}
