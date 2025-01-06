import dev.tesserakt.interop.rdfjs.n3.N3Quad
import dev.tesserakt.sparql.runtime.common.types.Bindings
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
//    val type: String?
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
@OptIn(ExperimentalJsExport::class)
@JsExport
data class QueryResultBindings(
    val variables: Array<String>,
    val value: Array<dynamic>
): QueryResult {

    internal constructor(variables: Iterable<String>, bindings: List<Bindings>): this(
        variables = variables.map { "?$it" }.toTypedArray(),
        value = bindings.map { it.mapKeys { "?${it.key}" }.toJsObject() }.toTypedArray(),
    )

    val type: String = "bindings"
    val checkOrder: Boolean = false

    override fun toString(): String {
        return JSON.stringify(this)
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null || this::class.js != other::class.js) return false

        other as QueryResultBindings

        if (!variables.contentEquals(other.variables)) return false
        if (!value.contentEquals(other.value)) return false
        if (type != other.type) return false
        if (checkOrder != other.checkOrder) return false

        return true
    }

    override fun hashCode(): Int {
        var result = variables.contentHashCode()
        result = 31 * result + value.contentHashCode()
        result = 31 * result + type.hashCode()
        result = 31 * result + checkOrder.hashCode()
        return result
    }

}

private fun <T> Map<String, T>.toJsObject(): dynamic {
    val result: dynamic = Any()
    forEach { result[it.key] = it.value }
    return result
}
