package dev.tesserakt.sparql.runtime.collection

import dev.tesserakt.sparql.runtime.collection.MappingArrayHint.Companion.PARTIAL_HASH_ACCESS
import dev.tesserakt.sparql.runtime.evaluation.BindingIdentifierSet
import dev.tesserakt.sparql.runtime.evaluation.context.QueryContext
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

fun MappingArray(
    context: QueryContext,
    bindings: Collection<String>,
    hint: MappingArrayHint = MappingArrayHint.DEFAULT,
) = when {
    bindings.isEmpty() -> SimpleMappingArray()
    bindings.size == 1 -> SingleHashMappingArray(context, bindings.first())
    !hint.requires(PARTIAL_HASH_ACCESS) -> CompleteHashMappingArray(context, bindings = bindings.toSet())
    else -> MultiHashMappingArray(context, bindings = bindings.toSet())
}

fun MappingArray(
    context: QueryContext,
    vararg bindings: String?,
    hint: MappingArrayHint = MappingArrayHint.DEFAULT,
): MappingArray {
    val set = setOfNotNull(*bindings)
    return MappingArray(context, set, hint)
}

fun ReindexableMappingArray(
    context: QueryContext,
    bindings: Collection<String>,
    hint: MappingArrayHint = MappingArrayHint.DEFAULT,
): ReindexableMappingArray {
    return ReindexableMappingArray(active = MappingArray(context, bindings, hint))
}

fun ReindexableMappingArray(
    bindings: BindingIdentifierSet,
    hint: MappingArrayHint = MappingArrayHint.DEFAULT,
): ReindexableMappingArray {
    return ReindexableMappingArray(active = MappingArray(bindings, hint))
}
