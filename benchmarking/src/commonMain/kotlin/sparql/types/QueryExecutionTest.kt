package sparql.types

import dev.tesserakt.rdf.types.Store
import dev.tesserakt.sparql.Compiler
import dev.tesserakt.sparql.Query
import dev.tesserakt.testing.Test

abstract class QueryExecutionTest(
    val queryString: String,
    val store: Store
): Test {

    val structure = Compiler().compile(queryString).structure
    val query = Query.Select(queryString)

}
