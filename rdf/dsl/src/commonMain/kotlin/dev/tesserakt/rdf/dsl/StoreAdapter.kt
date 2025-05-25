package dev.tesserakt.rdf.dsl

import dev.tesserakt.rdf.types.Quad
import dev.tesserakt.rdf.types.Store

class StoreAdapter(private val store: Store): RDF.Consumer {

    override fun process(subject: Quad.Subject, predicate: Quad.Predicate, `object`: Quad.Object, graph: Quad.Graph) {
        store.add(Quad(subject, predicate, `object`, graph))
    }

    override fun process(subject: Quad.BlankTerm, predicate: Quad.Predicate, `object`: Quad.Object, graph: Quad.Graph) {
        store.add(Quad(subject, predicate, `object`, graph))
    }

}
