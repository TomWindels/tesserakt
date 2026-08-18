package dev.tesserakt.sparql.runtime.collection

import dev.tesserakt.sparql.runtime.evaluation.BindingIdentifier
import dev.tesserakt.sparql.runtime.evaluation.BindingIdentifierSet
import dev.tesserakt.sparql.runtime.query.bindingIdentifierSetOf

data class MappingArrayHint(
    /**
     * Whether partial hash access is required.
     */
    var partialHashAccess: Boolean = false,
    /**
     * Whether all mappings that will be stored have a fixed, ahead of time known shape. If that is the case,
     *  the bitmask representing the shape has to be set here
     */
    var fixedShape: Int = -1,
)

inline fun MappingArray(
    bindings: BindingIdentifierSet,
    hint: MappingArrayHint = MappingArrayHint()
): MappingArray {
    return when {
        bindings.size == 0 -> {
            if (hint.fixedShape != -1) {
                SimpleFixedShapeMappingArray(hint.fixedShape)
            } else {
                SimpleMappingArray()
            }
        }
        bindings.size == 1 -> {
            val shape = hint.fixedShape
            SingleHashMappingArray(
                key = bindings[0],
                factory = if (shape != -1) {
                    { SimpleFixedShapeMappingArray(shape) }
                } else {
                    { SimpleMappingArray() }
                }
            )
        }
        !hint.partialHashAccess -> CompleteHashMappingArray(bindings)
        else -> MultiHashMappingArray(bindings)
    }
}

inline fun ReindexableMappingArray(
    vararg bindings: BindingIdentifier?,
    hint: MappingArrayHint = MappingArrayHint(),
): ReindexableMappingArray {
    val set = bindingIdentifierSetOf(*bindings)
    return ReindexableMappingArray(set, hint)
}

inline fun mappingArrayHint(block: MappingArrayHint.() -> Unit): MappingArrayHint {
    return MappingArrayHint().apply(block)
}
