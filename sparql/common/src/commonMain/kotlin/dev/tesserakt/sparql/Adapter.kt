package dev.tesserakt.sparql

import dev.tesserakt.rdf.types.Quad
import kotlin.jvm.JvmInline

@JvmInline
private value class BindingsAdapter(val inner: List<Pair<String, Quad.Term>>): Bindings {

    override fun iterator(): Iterator<Pair<String, Quad.Term>> = inner.iterator()

    override fun toString() = joinToString(prefix = "{", postfix = "}") { "${it.first} = ${it.second}" }

}

fun Map<String, Quad.Term>.toBindings(): Bindings = BindingsAdapter(map { it.toPair() })

fun List<Pair<String, Quad.Term>>.toBindings(): Bindings = BindingsAdapter(distinct())
