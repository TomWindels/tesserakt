package dev.tesserakt.sparql.endpoint.client

import dev.tesserakt.sparql.endpoint.core.SparqlContentType
import dev.tesserakt.sparql.endpoint.core.data.SelectResponse
import io.ktor.http.*
import io.ktor.http.content.*
import io.ktor.serialization.*
import io.ktor.util.reflect.*
import io.ktor.utils.io.*
import io.ktor.utils.io.charsets.*
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.io.decodeFromSource


internal object SparqlBindingsContentConverter : ContentConverter {

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
            contentType = SparqlContentType.JsonBindings.withCharset(charset),
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
        val response: SelectResponse = if (charset != Charsets.UTF_8) {
            @OptIn(InternalAPI::class)
            val text = charset.newDecoder().decode(input = content.readBuffer)
            Json.decodeFromString(text)
        } else {
            @OptIn(ExperimentalSerializationApi::class, InternalAPI::class)
            Json.decodeFromSource(content.readBuffer)
        }
        return if (typeInfo.type == SelectResponse::class) {
            response
        } else {
            response.toBindings()
        }
    }

}
