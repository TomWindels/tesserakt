package tesserakt.rdf

import tesserakt.createTestStore
import tesserakt.rdf.ontology.RDF
import tesserakt.rdf.types.Store
import tesserakt.rdf.types.Triple
import tesserakt.rdf.types.Triple.Companion.asNamedTerm
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
            predicate = "age".asNamedTerm(),
            `object` = null
        ).forEach {
            ((it.o as? Triple.Literal<*>)?.literal as? Int)?.let { age = it }
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
