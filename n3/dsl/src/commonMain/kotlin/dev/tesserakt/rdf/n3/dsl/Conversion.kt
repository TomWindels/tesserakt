package dev.tesserakt.rdf.n3.dsl

import dev.tesserakt.rdf.n3.ExperimentalN3Api
import dev.tesserakt.rdf.n3.Quad

@ExperimentalN3Api
fun dev.tesserakt.rdf.types.Quad.Term.toN3Term() = Quad.Term.RdfTerm(this)
