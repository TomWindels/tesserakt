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

fun RehashableMappingArray(context: QueryContext, bindings: Collection<String>): RehashableMappingArray {
    return RehashableMappingArray(active = MappingArray(context, bindings))
}

fun RehashableMappingArray(bindings: BindingIdentifierSet): RehashableMappingArray {
    return RehashableMappingArray(active = MappingArray(bindings))
}
