package dev.tesserakt.sparql.runtime.evaluation

import dev.tesserakt.sparql.runtime.evaluation.context.QueryContext
import kotlin.jvm.JvmInline

@JvmInline
value class BindingIdentifier(val id: Int) {

    constructor(context: QueryContext, name: String): this(id = context.resolveBinding(name))

    companion object {
        fun QueryContext.get(binding: BindingIdentifier): String = resolveBinding(id = binding.id)
    }

}
