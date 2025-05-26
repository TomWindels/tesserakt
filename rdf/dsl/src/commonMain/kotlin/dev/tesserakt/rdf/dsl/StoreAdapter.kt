package dev.tesserakt.rdf.dsl

import dev.tesserakt.rdf.types.MutableStore
import dev.tesserakt.rdf.types.Quad

class StoreAdapter(private val store: MutableStore): RDF.Consumer {

    override fun process(subject: Quad.Subject, predicate: Quad.Predicate, `object`: Quad.Object, graph: Quad.Graph) {
        store.add(Quad(subject, predicate, `object`, graph))
    }

    override fun process(subject: Quad.BlankTerm, predicate: Quad.Predicate, `object`: Quad.Object, graph: Quad.Graph) {
        store.add(Quad(subject, predicate, `object`, graph))
    }

}
