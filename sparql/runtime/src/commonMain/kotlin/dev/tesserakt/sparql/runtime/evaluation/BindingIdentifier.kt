package dev.tesserakt.sparql.runtime.evaluation

class BindingIdentifier private constructor(
    // the name, or `null` if not available
    val name: String?,
    val id: Int,
) {

    constructor(context: QueryContext, name: String): this(name = name, id = context.resolveBinding(name))

    constructor(id: Int): this(name = null, id = id)

    override fun equals(other: Any?): Boolean {
        if (this === other) {
            return true
        }
        if (other !is BindingIdentifier) {
            return false
        }
        // assuming same context
        return id == other.id
    }

    override fun hashCode(): Int {
        // assuming same context
        return id
    }

    override fun toString(): String {
        return name ?: "?"
    }

    companion object {
        fun QueryContext.get(binding: BindingIdentifier): String = resolveBinding(id = binding.id)
    }

}
