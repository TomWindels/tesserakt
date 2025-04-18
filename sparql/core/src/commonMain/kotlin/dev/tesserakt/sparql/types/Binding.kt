package dev.tesserakt.sparql.types

import kotlin.jvm.JvmInline

@JvmInline
value class Binding(
    /**
     * The name of the binding, without prefix `?`
     */
    val name: String
) {
    override fun toString() = "?$name"
}
