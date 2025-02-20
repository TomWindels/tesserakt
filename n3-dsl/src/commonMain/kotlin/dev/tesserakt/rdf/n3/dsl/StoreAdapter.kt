package dev.tesserakt.rdf.n3.dsl

import dev.tesserakt.rdf.n3.ExperimentalN3Api
import dev.tesserakt.rdf.n3.Quad
import dev.tesserakt.rdf.n3.Store

@ExperimentalN3Api
class StoreAdapter(private val store: Store): N3Context.Consumer {

    override fun process(subject: Quad.Term, predicate: Quad.Term, `object`: Quad.Term) {
        store.add(Quad(subject, predicate, `object`))
    }

}
