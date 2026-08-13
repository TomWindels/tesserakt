package dev.tesserakt.sparql.runtime.evaluation.context

import dev.tesserakt.rdf.types.Store
import dev.tesserakt.sparql.types.QueryStructure
import dev.tesserakt.sparql.types.extractAllBindings

fun QueryContext(ast: QueryStructure): QueryContext {
    return when {
        ast.body.extractAllBindings().size < 32 -> BitsetQueryContext(ast)
        else -> throw UnsupportedOperationException("Queries containing more than 32 distinct binding identifiers are currently unsupported!")
    }
}

fun QueryContext(source: Store?, ast: QueryStructure): QueryContext {
    return when {
        ast.body.extractAllBindings().size < 32 -> {
            if (source != null) {
                StoreBackedQueryContext(ast, source)
            } else {
                BitsetQueryContext(ast)
            }
        }
        else -> throw UnsupportedOperationException("Queries containing more than 32 distinct binding identifiers are currently unsupported!")
    }
}
