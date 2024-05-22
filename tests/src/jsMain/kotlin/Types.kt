import kotlin.js.Promise

// interface ported from https://github.com/rubensworks/rdf-test-suite.js/blob/master/lib/testcase/sparql/IQueryEngine.ts

external interface Quad {
    val subject: Term
    val predicate: Term
    val `object`: Term
    val graph: Term
}

external interface Term {
    val value: String
    val termType: String
}

/**
 * A query engine handler.
 */
external interface QueryEngine {
    fun parse(queryString: String, options: dynamic): Promise<Unit>
    fun query(data: Array<Quad>, queryString: String, options: dynamic): Promise<QueryResult<*>>
}

/**
 * Super type for all query result types.
 */
external interface QueryResult<T> {
    val type: String?
    val value: T
}

/**
 * Holds a boolean query result.
 */
external interface IQueryResultBoolean: QueryResult<Boolean> {
//     type: 'boolean
    fun <T> equals(that: QueryResult<T>, laxCardinality: Boolean?): Boolean
}

/**
 * Holds quad-based query results.
 */
external interface QueryResultQuads: QueryResult<Array<Quad>> {
//    type: 'quads'
    fun <T> equals(that: QueryResult<T>, laxCardinality: Boolean?): Boolean
}

/**
 * Holds bindings-based query results.
 */
external interface IQueryResultBindings: QueryResult<Map<String, Term>> {
//    type: 'bindings'
    val variables: Array<String>
    val checkOrder: Boolean
    fun <T> equals(that: QueryResult<T>, laxCardinality: Boolean?): Boolean
}
