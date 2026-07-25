package dev.tesserakt.sparql.runtime.collection

import dev.tesserakt.sparql.runtime.collection.MappingArrayHint.Companion.PARTIAL_HASH_ACCESS
import dev.tesserakt.sparql.runtime.evaluation.BindingIdentifier
import dev.tesserakt.sparql.runtime.evaluation.BindingIdentifierSet
import dev.tesserakt.sparql.runtime.query.bindingIdentifierSetOf
import kotlin.jvm.JvmInline

@JvmInline
value class MappingArrayHint private constructor(private val mask: Int) {

    constructor(
        partialHashAccess: Boolean = false,
    ): this(
        mask =
            bit(PARTIAL_HASH_ACCESS, partialHashAccess)
    )

    companion object {

        val DEFAULT = MappingArrayHint()

        internal const val PARTIAL_HASH_ACCESS = 0

        private fun bit(index: Int, set: Boolean) = if (set) 1 shl index else 0

    }

    fun requires(feature: Int): Boolean = (mask and (1 shl feature)) != 0

}

fun MappingArray(
    bindings: BindingIdentifierSet,
    hint: MappingArrayHint = MappingArrayHint.DEFAULT,
) = when {
    bindings.size == 0 -> SimpleMappingArray()
    bindings.size == 1 -> SingleHashMappingArray(bindings[0])
    !hint.requires(PARTIAL_HASH_ACCESS) -> CompleteHashMappingArray(bindings)
    else -> MultiHashMappingArray(bindings)
}

fun ReindexableMappingArray(
    vararg bindings: BindingIdentifier?,
    hint: MappingArrayHint = MappingArrayHint.DEFAULT,
): ReindexableMappingArray {
    val set = bindingIdentifierSetOf(*bindings)
    return ReindexableMappingArray(active = MappingArray(set, hint))
}

fun ReindexableMappingArray(
    bindings: BindingIdentifierSet,
    hint: MappingArrayHint = MappingArrayHint.DEFAULT,
): ReindexableMappingArray {
    return ReindexableMappingArray(active = MappingArray(bindings, hint))
}
