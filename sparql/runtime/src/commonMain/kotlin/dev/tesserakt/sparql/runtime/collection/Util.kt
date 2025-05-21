package dev.tesserakt.sparql.runtime.collection

import dev.tesserakt.sparql.runtime.evaluation.QueryContext

fun MappingArray(context: QueryContext, bindings: Collection<String>) = when (bindings.size) {
    0 -> SimpleMappingArray()
    1 -> SingleHashMappingArray(context, bindings.first())
    else -> MultiHashMappingArray(context, bindings = bindings.toSet())
}

fun MappingArray(context: QueryContext, vararg bindings: String?): MappingArray {
    val set = setOfNotNull(*bindings)
    return MappingArray(context, set)
}
