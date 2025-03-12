package dev.tesserakt.sparql.runtime.collection

fun MappingArray(bindings: Collection<String>) = when {
    bindings.isNotEmpty() -> MultiHashMappingArray(bindings = bindings.toSet())
    else -> SimpleMappingArray()
}

fun MappingArray(vararg bindings: String?): MappingArray {
    val set = setOfNotNull(*bindings)
    return MappingArray(set)
}
