package dev.tesserakt.sparql.runtime.collection

import dev.tesserakt.sparql.runtime.evaluation.QueryContext

fun MappingArray(context: QueryContext, bindings: Collection<String>) = when {
    bindings.isNotEmpty() -> MultiHashMappingArray(context, bindings = bindings.toSet())
    else -> SimpleMappingArray()
}

fun MappingArray(context: QueryContext, vararg bindings: String?): MappingArray {
    val set = setOfNotNull(*bindings)
    return MappingArray(context, set)
}
