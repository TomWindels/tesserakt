package dev.tesserakt.sparql.runtime.evaluation.context

import dev.tesserakt.rdf.types.EncodingContext
import dev.tesserakt.sparql.types.QueryStructure
import dev.tesserakt.sparql.types.extractAllBindings

fun QueryContext(ast: QueryStructure): QueryContext {
    return when {
        ast.body.extractAllBindings().distinct().size < 32 -> BitsetQueryContext(ast)
        else -> IntPairQueryContext(ast)
    }
}

fun QueryContext(context: EncodingContext?, ast: QueryStructure): QueryContext {
    return when {
        ast.body.extractAllBindings().distinct().size < 32 -> {
            if (context != null) {
                StoreBackedQueryContext(ast, context)
            } else {
                BitsetQueryContext(ast)
            }
        }
        else -> IntPairQueryContext(ast)
    }
}
