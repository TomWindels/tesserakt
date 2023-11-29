package core.rdf.dsl

import core.rdf.types.Store
import core.rdf.types.Triple

class StoreAdapter(private val store: Store): RdfContext.Consumer {

    override fun process(subject: Triple.NamedTerm, predicate: Triple.NamedTerm, `object`: Triple.Term) {
        store.add(Triple(subject, predicate, `object`))
    }

    override fun process(subject: Triple.BlankTerm, predicate: Triple.NamedTerm, `object`: Triple.Term) {
        store.add(Triple(subject, predicate, `object`))
    }

}
