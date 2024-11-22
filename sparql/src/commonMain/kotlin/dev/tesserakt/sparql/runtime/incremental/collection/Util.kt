package dev.tesserakt.sparql.runtime.incremental.collection

internal fun mutableJoinCollection(bindings: Collection<String>) = when {
    bindings.isNotEmpty() -> HashJoinArray(bindings = bindings.toSet())
    else -> NestedJoinArray()
}

internal fun mutableJoinCollection(vararg bindings: String?): JoinCollection {
    val set = setOfNotNull(*bindings)
    return mutableJoinCollection(set)
}
