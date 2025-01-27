package sparql.types

import dev.tesserakt.rdf.types.Store

data class QueryExecutionTest(
    val query: String,
    val store: Store
) {

    fun toOutputComparisonTest() = OutputComparisonTest(query = query, store = store)

    fun toIncrementalUpdateTest() = IncrementalUpdateTest(query = query, store = store)

    fun toRandomUpdateTest() = RandomUpdateTest(query = query, store = store)

}
