package dev.tesserakt.sparql

import dev.tesserakt.rdf.types.Quad

interface Bindings: Iterable<Pair<String, Quad.Term>>
