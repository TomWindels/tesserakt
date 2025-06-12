import dev.tesserakt.rdf.types.Quad.Companion.asLiteralTerm
import dev.tesserakt.rdf.types.Quad.Companion.asNamedTerm
import dev.tesserakt.sparql.endpoint.client.*
import dev.tesserakt.sparql.endpoint.server.sparqlEndpoint
import io.ktor.client.*
import io.ktor.client.plugins.api.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.client.request.forms.*
import io.ktor.client.statement.*
import io.ktor.client.utils.*
import io.ktor.http.*
import io.ktor.server.routing.*
import io.ktor.server.testing.*
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals

class SparqlEndpointTest {

    @Test
    fun getSelectAll() = test { client ->
        val response = client.sparqlQuery("sparql", "select * { ?s ?p ?o }")
        assertEquals(HttpStatusCode.OK, response.status)
        val data = response.bodyAsBindings()
        assert(data.isEmpty())
    }

    @Test
    fun insertTest() = test { client ->
        val response = client.sparqlUpdate {
            insert {
                "user".asNamedTerm() has "name".asNamedTerm() being "Test".asLiteralTerm()
            }
        }
        assertEquals(HttpStatusCode.OK, response.status)
    }

    @Test
    fun insertAndQueryTest() = test { client ->
        val selectAll = "select * { ?s ?p ?o }"

        val select1 = client.sparqlQuery("sparql", selectAll)
        assertEquals(select1.status, HttpStatusCode.OK)
        assert(select1.bodyAsBindings().isEmpty())

        val insertion = client.sparqlUpdate {
            insert {
                "user".asNamedTerm() has "name".asNamedTerm() being "Test".asLiteralTerm()
            }
        }
        assertEquals(HttpStatusCode.OK, insertion.status)

        val select2 = client.sparqlQuery("sparql", selectAll)
        assertEquals(HttpStatusCode.OK, select2.status)
        assertContentEquals(
            expected = listOf(mapOf("s" to "user", "p" to "name", "o" to "Test")),
            actual = select2.bodyAsBindings().map { it.toMap().mapValues { it.value.value } }
        )
    }

    @Test
    fun insertAndDeleteGetTest() {
        insertAndDeletionTest(mode = QueryOperationMode.GET)
    }

    @Test
    fun insertAndDeletePostFormTest() {
        insertAndDeletionTest(mode = QueryOperationMode.POST_FORM)
    }

    @Test
    fun insertAndDeletePostBodyTest() {
        insertAndDeletionTest(mode = QueryOperationMode.POST_BODY)
    }

    @Test
    fun patchRequest() = test { client ->
        val response = client.patch("sparql")
        // a PATCH request is invalid, but the slug has to be in use, so checking the status code
        assertEquals(HttpStatusCode.MethodNotAllowed, response.status)
    }

    private fun insertAndDeletionTest(mode: QueryOperationMode) = test { client ->
        val selectAll = "select * { ?s ?p ?o }"

        val select1 = client.sparqlQuery("sparql", selectAll, mode = mode)
        assertEquals(HttpStatusCode.OK, select1.status)
        assert(select1.bodyAsBindings().isEmpty())

        val insertion = client.sparqlUpdate {
            insert {
                "user".asNamedTerm() has "name".asNamedTerm() being "Test".asLiteralTerm()
            }
        }
        assertEquals(HttpStatusCode.OK, insertion.status)

        val select2 = client.sparqlQuery("sparql", selectAll, mode = mode)
        assertEquals(HttpStatusCode.OK, select2.status)
        assertContentEquals(
            expected = listOf(mapOf("s" to "user", "p" to "name", "o" to "Test")),
            actual = select2.bodyAsBindings().map { it.toMap().mapValues { it.value.value } }
        )

        val deletion = client.sparqlUpdate {
            delete {
                "user".asNamedTerm() has "name".asNamedTerm() being "Test".asLiteralTerm()
            }
        }
        assertEquals(HttpStatusCode.OK, deletion.status)

        val select3 = client.sparqlQuery("sparql", selectAll, mode = mode)
        assertEquals(HttpStatusCode.OK, select3.status)
        assert(select3.bodyAsBindings().isEmpty())
    }

    private inline fun test(crossinline block: suspend (HttpClient) -> Unit) = testApplication {
        application {
            routing {
                sparqlEndpoint(
                    json = Json {
                        prettyPrint = true
                    }
                )
            }
        }
        val client = createClient {
            install(ContentNegotiation) {
                sparql()
            }
            install(createClientPlugin("debug") {
                onRequest { request, content ->
                    println("*")
                    println(">  ${request.url.protocol.name.uppercase()} ${request.method.value} ${request.url.toString().removePrefix("${request.url.protocol.name}://${request.url.host}")}")
                    val headers = request.headers.entries().joinToString("\n") { ">  ${it.key}: ${it.value.joinToString()}" }
                    if (headers.isNotBlank()) {
                        println(headers)
                    }
                    if (content is String) {
                        println(content.prependIndent(">> "))
                    } else if (content is EmptyContent) {
                        // nothing to do
                    } else if (content is FormDataContent) {
                        val data = content.formData.entries().joinToString("\n") { ">> ${it.key}: ${it.value.joinToString()}" }
                        if (data.isNotBlank()) {
                            println(data)
                        }
                    } else {
                        println(">> `${content::class.simpleName}`")
                    }
                }

                onResponse { response ->
                    println("<  ${response.status}")
                    val headers = response.headers.entries().joinToString("\n") { "<  ${it.key}: ${it.value.joinToString()}" }
                    if (headers.isNotBlank()) {
                        println(headers)
                    }
                    val responseAsText = response.bodyAsText()
                    if (responseAsText.isNotBlank()) {
                        println(responseAsText.prependIndent("<< "))
                    }
                    println()
                }
            })
        }
        block(client)
    }

}
