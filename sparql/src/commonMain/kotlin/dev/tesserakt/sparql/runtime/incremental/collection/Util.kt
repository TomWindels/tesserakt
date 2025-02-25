package dev.tesserakt.sparql.runtime.incremental.collection

internal fun MappingArray(bindings: Collection<String>) = when {
    bindings.isNotEmpty() -> MultiHashMappingArray(bindings = bindings.toSet())
    else -> SimpleMappingArray()
}

internal fun MappingArray(vararg bindings: String?): MappingArray {
    val set = setOfNotNull(*bindings)
    return MappingArray(set)
}
