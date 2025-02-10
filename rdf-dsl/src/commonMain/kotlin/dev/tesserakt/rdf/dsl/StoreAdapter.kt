package dev.tesserakt.rdf.dsl

import dev.tesserakt.rdf.types.Quad
import dev.tesserakt.rdf.types.Store

class StoreAdapter(private val store: Store): RDF.Consumer {

    override fun process(subject: Quad.NamedTerm, predicate: Quad.NamedTerm, `object`: Quad.Term, graph: Quad.Graph) {
        store.add(Quad(subject, predicate, `object`, graph))
    }

    override fun process(subject: Quad.BlankTerm, predicate: Quad.NamedTerm, `object`: Quad.Term, graph: Quad.Graph) {
        store.add(Quad(subject, predicate, `object`, graph))
    }

}
