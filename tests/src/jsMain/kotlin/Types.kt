import dev.tesserakt.interop.rdfjs.n3.N3Quad
import kotlin.js.Promise

// interface ported from https://github.com/rubensworks/rdf-test-suite.js/blob/master/lib/testcase/sparql/IQueryEngine.ts

/**
 * A query engine handler.
 */
external interface QueryEngine {
    fun parse(queryString: String, options: Map<String, dynamic>): Promise<Unit>
    fun query(data: Array<N3Quad>, queryString: String, options: Map<String, dynamic>): Promise<QueryResult>
}

/**
 * Super type for all query result types.
 */
external interface QueryResult {
    val type: String?
}

/**
 * Holds a boolean query result.
 */
external interface IQueryResultBoolean: QueryResult {
//     type: 'boolean
}

/**
 * Holds quad-based query results.
 */
external interface QueryResultQuads: QueryResult {
//    type: 'quads'
}

/**
 * Holds bindings-based query results.
 */
external interface IQueryResultBindings: QueryResult {
//    type: 'bindings'
    val variables: Array<String>
    val checkOrder: Boolean
}
