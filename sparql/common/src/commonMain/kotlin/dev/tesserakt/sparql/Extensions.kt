package dev.tesserakt.sparql

import dev.tesserakt.rdf.types.Quad

operator fun Bindings.get(name: String): Quad.Term? = find { it.first == name }?.second
