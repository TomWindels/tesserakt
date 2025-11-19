package dev.tesserakt.sparql.runtime.collection

import dev.tesserakt.sparql.runtime.evaluation.BindingIdentifierSet
import dev.tesserakt.sparql.runtime.evaluation.context.QueryContext

fun MappingArray(bindings: BindingIdentifierSet) = when (bindings.size) {
    0 -> SimpleMappingArray()
    1 -> SingleHashMappingArray(bindings[0])
    else -> MultiHashMappingArray(bindings)
}

fun MappingArray(context: QueryContext, bindings: Collection<String>) = when (bindings.size) {
    0 -> SimpleMappingArray()
    1 -> SingleHashMappingArray(context, bindings.first())
    else -> MultiHashMappingArray(context, bindings = bindings.toSet())
}

fun MappingArray(context: QueryContext, vararg bindings: String?): MappingArray {
    val set = setOfNotNull(*bindings)
    return MappingArray(context, set)
}

fun ReindexableMappingArray(context: QueryContext, bindings: Collection<String>): ReindexableMappingArray {
    return ReindexableMappingArray(active = MappingArray(context, bindings))
}

fun ReindexableMappingArray(bindings: BindingIdentifierSet): ReindexableMappingArray {
    return ReindexableMappingArray(active = MappingArray(bindings))
}
