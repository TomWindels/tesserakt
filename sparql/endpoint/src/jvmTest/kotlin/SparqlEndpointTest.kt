import dev.tesserakt.rdf.types.Quad.Companion.asLiteralTerm
import dev.tesserakt.rdf.types.Quad.Companion.asNamedTerm
import dev.tesserakt.sparql.endpoint.*
import io.ktor.client.*
import io.ktor.client.plugins.api.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.client.utils.*
import io.ktor.http.*
import io.ktor.server.testing.*
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals

class SparqlEndpointTest {

    @Test
    fun getSelectAll() = test { client ->
        val response = client.sparqlQuery("select * { ?s ?p ?o }")
        assertEquals(response.status, HttpStatusCode.OK)
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
        assertEquals(response.status, HttpStatusCode.OK)
    }

    @Test
    fun insertAndQueryTest() = test { client ->
        val selectAll = "select * { ?s ?p ?o }"

        val select1 = client.sparqlQuery(selectAll)
        assertEquals(select1.status, HttpStatusCode.OK)
        assert(select1.bodyAsBindings().isEmpty())

        val insertion = client.sparqlUpdate {
            insert {
                "user".asNamedTerm() has "name".asNamedTerm() being "Test".asLiteralTerm()
            }
        }
        assertEquals(insertion.status, HttpStatusCode.OK)

        val select2 = client.sparqlQuery(selectAll)
        assertEquals(select2.status, HttpStatusCode.OK)
        assertContentEquals(
            expected = listOf(mapOf("s" to "user", "p" to "name", "o" to "Test")),
            actual = select2.bodyAsBindings().map { it.toMap().mapValues { it.value.value } }
        )
    }

    @Test
    fun insertAndDeleteTest() = test { client ->
        val selectAll = "select * { ?s ?p ?o }"

        val select1 = client.sparqlQuery(selectAll)
        assertEquals(select1.status, HttpStatusCode.OK)
        assert(select1.bodyAsBindings().isEmpty())

        val insertion = client.sparqlUpdate {
            insert {
                "user".asNamedTerm() has "name".asNamedTerm() being "Test".asLiteralTerm()
            }
        }
        assertEquals(insertion.status, HttpStatusCode.OK)

        val select2 = client.sparqlQuery(selectAll)
        assertEquals(select2.status, HttpStatusCode.OK)
        assertContentEquals(
            expected = listOf(mapOf("s" to "user", "p" to "name", "o" to "Test")),
            actual = select2.bodyAsBindings().map { it.toMap().mapValues { it.value.value } }
        )

        val deletion = client.sparqlUpdate {
            remove {
                "user".asNamedTerm() has "name".asNamedTerm() being "Test".asLiteralTerm()
            }
        }
        assertEquals(deletion.status, HttpStatusCode.OK)

        val select3 = client.sparqlQuery(selectAll)
        assertEquals(select3.status, HttpStatusCode.OK)
        assert(select3.bodyAsBindings().isEmpty())
    }

    @Test
    fun patchRequest() = test { client ->
        val response = client.patch("sparql")
        // a PATCH request is invalid, but the slug has to be in use, so checking the status code
        assertEquals(response.status, HttpStatusCode.MethodNotAllowed)
    }

    private inline fun test(crossinline block: suspend (HttpClient) -> Unit) = testApplication {
        application {
            sparqlEndpoint()
        }
        val client = createClient {
            install(ContentNegotiation) {
                sparql()
            }
            install(createClientPlugin("debug") {
                onRequest { request, content ->
                    println("> ${request.url.protocol.name.uppercase()} ${request.method.value} ${request.url.toString().removePrefix("${request.url.protocol.name}://${request.url.host}")}")
                    val headers = request.headers.entries().joinToString("\n") { "> ${it.key}: ${it.value}" }
                    if (headers.isNotBlank()) {
                        println(headers)
                    }
                    val contentAsText = if (content !is EmptyContent) content.toString() else null
                    if (!contentAsText.isNullOrBlank()) {
                        println(contentAsText.prependIndent("> "))
                    }
                }

                onResponse { response ->
                    println("< ${response.status}")
                    val headers = response.headers.entries().joinToString("\n") { "< ${it.key}: ${it.value}" }
                    if (headers.isNotBlank()) {
                        println(headers)
                    }
                    val responseAsText = response.bodyAsText()
                    if (responseAsText.isNotBlank()) {
                        println(responseAsText.prependIndent("< "))
                    }
                }
            })
        }
        block(client)
    }

}
