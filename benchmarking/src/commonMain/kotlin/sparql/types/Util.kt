package sparql.types

import dev.tesserakt.rdf.types.Store
import dev.tesserakt.testing.TestEnvironment


fun TestEnvironment.using(store: Store) = TestBuilder(environment = this, store = store)

class TestBuilder(private val environment: TestEnvironment, private val store: Store) {

    infix fun test(query: String) {
        environment.add(OutputComparisonTest(query = query, store = store))
    }

}
