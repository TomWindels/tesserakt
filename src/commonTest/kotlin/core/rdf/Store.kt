package core.rdf

import core.rdf.dsl.Builder.Companion.buildStore
import core.rdf.ontology.RDF
import core.rdf.types.Triple.Companion.asNamedNode
import kotlin.test.Test

class Store {

    @Test
    fun createAndFilterBasicStore() {
        val store = buildStore {
            +"<test>".asNamedNode() has RDF.type being "<my-type>".asNamedNode()
            +"<test>".asNamedNode() has "<count>".asNamedNode() being 5
        }
        store
            .filter("<test>".asNamedNode(), "<count>".asNamedNode(), null)
            .forEach { println("Found $it!") }
    }


}
