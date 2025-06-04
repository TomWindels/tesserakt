import dev.tesserakt.sparql.endpoint.sparqlEndpoint
import io.ktor.client.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.server.testing.*
import kotlin.test.Test
import kotlin.test.assertEquals

class SparqlEndpointTest {

    @Test
    fun setup1() = test { client ->
        val response = client.get("sparql")
        println("[${response.status}] ${response.bodyAsText()}")
        // a GET request is not supported, so should not exist
        assertEquals(response.status, HttpStatusCode.NotFound)
    }

    private inline fun test(crossinline block: suspend (HttpClient) -> Unit) = testApplication {
        application {
            sparqlEndpoint()
        }
    }

}
