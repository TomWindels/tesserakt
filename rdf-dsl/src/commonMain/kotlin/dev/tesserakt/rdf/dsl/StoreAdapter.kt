package dev.tesserakt.rdf.dsl

import dev.tesserakt.rdf.types.Quad
import dev.tesserakt.rdf.types.Store

class StoreAdapter(private val store: Store): RdfContext.Consumer {

    override fun process(subject: Quad.NamedTerm, predicate: Quad.NamedTerm, `object`: Quad.Term) {
        store.add(Quad(subject, predicate, `object`))
    }

    override fun process(subject: Quad.BlankTerm, predicate: Quad.NamedTerm, `object`: Quad.Term) {
        store.add(Quad(subject, predicate, `object`))
    }

}
