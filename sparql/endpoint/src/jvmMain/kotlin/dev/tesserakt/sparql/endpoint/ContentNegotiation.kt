package dev.tesserakt.sparql.endpoint

import dev.tesserakt.rdf.dsl.RDF
import dev.tesserakt.rdf.dsl.buildStore
import dev.tesserakt.rdf.trig.serialization.TriGSerializer
import dev.tesserakt.rdf.types.MutableStore
import dev.tesserakt.rdf.types.Quad
import dev.tesserakt.rdf.types.Store
import dev.tesserakt.rdf.types.factory.mutableStoreOf
import dev.tesserakt.sparql.Bindings
import dev.tesserakt.sparql.endpoint.data.SelectResponse
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.http.content.*
import io.ktor.serialization.*
import io.ktor.util.reflect.*
import io.ktor.utils.io.*
import io.ktor.utils.io.charsets.*
import io.ktor.utils.io.jvm.javaio.*
import kotlinx.serialization.json.Json
import io.ktor.client.plugins.contentnegotiation.ContentNegotiationConfig as ClientContentNegotiationConfig
import io.ktor.server.plugins.contentnegotiation.ContentNegotiationConfig as ServerContentNegotiationConfig

fun ServerContentNegotiationConfig.sparql() {
    register(SparqlBindingsType, SparqlBindingsContentConverter)
}

fun ClientContentNegotiationConfig.sparql() {
    register(SparqlBindingsType, SparqlBindingsContentConverter)
}

suspend fun HttpClient.sparqlQuery(query: String, path: String = "sparql"): HttpResponse {
    return get(path) {
        parameter("query", query)
    }
}

suspend inline fun HttpClient.sparqlUpdate(path: String = "sparql", block: SparqlUpdateRequestBuilder.() -> Unit): HttpResponse {
    val content = SparqlUpdateRequestBuilder().apply(block).optimise()
    return post(path) {
        contentType(SparqlUpdateQueryType)
        setBody(content.toQueryString())
    }
}

class SparqlUpdateRequestBuilder(
    internal val additions: MutableStore = mutableStoreOf(),
    internal val deletions: MutableStore = mutableStoreOf(),
) {
    fun optimise(): SparqlUpdateRequestBuilder {
        val common = additions.intersect(deletions)
        if (common.isEmpty()) {
            return this
        }
        additions.removeAll(common)
        deletions.removeAll(common)
        return this
    }

    fun toQueryString(): String = buildString {
        if (additions.isNotEmpty()) {
            append("INSERT DATA { ")
            TriGSerializer.serialize(additions).forEach { append(it) }
            append(" }")
        }
        if (additions.isNotEmpty() && deletions.isNotEmpty()) {
            append('\n')
        }
        if (deletions.isNotEmpty()) {
            append("DELETE DATA { ")
            TriGSerializer.serialize(deletions).forEach { append(it) }
            append(" }")
        }
    }
}

fun SparqlUpdateRequestBuilder.insert(builder: RDF.() -> Unit) {
    additions.addAll(buildStore(block = builder))
}

fun SparqlUpdateRequestBuilder.remove(builder: RDF.() -> Unit) {
    deletions.addAll(buildStore(block = builder))
}

fun SparqlUpdateRequestBuilder.add(quad: Quad) {
    additions.add(quad)
}

fun SparqlUpdateRequestBuilder.add(data: Store) {
    additions.addAll(data)
}

fun SparqlUpdateRequestBuilder.delete(quad: Quad) {
    deletions.add(quad)
}

fun SparqlUpdateRequestBuilder.delete(data: Store) {
    deletions.addAll(data)
}

suspend fun HttpResponse.bodyAsBindings(): List<Bindings> {
    return body<List<Bindings>>()
}

object SparqlBindingsContentConverter: ContentConverter {

    override suspend fun serialize(
        contentType: ContentType,
        charset: Charset,
        typeInfo: TypeInfo,
        value: Any?
    ): OutgoingContent? {
        if (typeInfo.type != SelectResponse::class) {
            return null
        }
        value as SelectResponse
        return TextContent(
            text = Json.encodeToString(value),
            contentType = SparqlBindingsType.withCharset(charset),
            status = HttpStatusCode.OK
        )
    }

    override suspend fun deserialize(
        charset: Charset,
        typeInfo: TypeInfo,
        content: ByteReadChannel
    ): Any? {
        if (typeInfo.type != SelectResponse::class && typeInfo.type.isInstance(List::class)) {
            return null
        }
        // can't use the stream variant, as there's no support for the InputStreamReader class, which
        //  uses the actual charset
        val stream = content.toInputStream().reader(charset).readText()
        val response = Json.decodeFromString<SelectResponse>(stream)
        return if (typeInfo.type == SelectResponse::class) {
            response
        } else {
            response.toBindings()
        }
    }

}
