package rdf

import FOAF
import createTestStore
import dev.tesserakt.rdf.ontology.RDF
import dev.tesserakt.rdf.types.Quad
import dev.tesserakt.rdf.types.Quad.Companion.asNamedTerm
import dev.tesserakt.rdf.types.Store
import kotlin.test.Test
import kotlin.test.assertSame

class StoreTest {

    @Test
    fun basicStoreUsage() {
        val store = createTestStore()
        println("Store contents: $store")
        store.validateAge()
        store.validateNotes()
    }

    private fun Store.validateAge() {
        var age = -1
        filter(
            subject = "person1".asNamedTerm(),
            predicate = FOAF.age,
            `object` = null
        ).forEach {
            ((it.o as? Quad.Literal<*>)?.literal as? Int)?.let { age = it }
        }
        assertSame(age, 23, "Age was not correctly found in the initial store (found $age)!")
    }

    private fun Store.validateNotes() {
        var noteItems = 0
        filter(
            subject = null,
            predicate = RDF.rest,
            `object` = null
        ).forEach { _ -> ++noteItems }
        assertSame(noteItems, 2, "Number of notes did not match in the store (found $noteItems)!")
    }


}
