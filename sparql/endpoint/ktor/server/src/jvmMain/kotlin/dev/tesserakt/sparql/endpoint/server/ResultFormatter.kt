package dev.tesserakt.sparql.endpoint.server

import dev.tesserakt.sparql.endpoint.core.SparqlContentType
import dev.tesserakt.sparql.endpoint.core.data.SelectResponse
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.utils.io.jvm.javaio.*
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.encodeToStream

class ResultFormatter(
    private val json: Json = Json,
) {

    internal suspend fun respond(
        call: ApplicationCall,
        result: Result<SelectResponse>,
    ) {
        result.fold(
            onSuccess = { response ->
                val accept = call.request.acceptItems().map { it.value }
                // going through the accept headers in order, first one that matches a compatible format
                //  is chosen; bailing upon match
                accept.forEach { format ->
                    when {
                        SparqlContentType.JsonBindings.match(format) -> {
                            respondJson(call, SelectResponse.serializer(), response)
                            return@fold
                        }

                        SparqlContentType.XmlBindings.match(format) -> {
                            respondXml(call, response)
                            return@fold
                        }
                    }
                }
                // none matched, responding with that instead
                call.respond(
                    status = HttpStatusCode.BadRequest,
                    message = "Invalid Accept headers!"
                )
            },
            onFailure = { cause ->
                call.respond(
                    status = HttpStatusCode.BadRequest,
                    message = "Invalid query! Caught the following exception.\n${cause.message}"
                )
            }
        )
    }

    private suspend fun <T> respondJson(call: ApplicationCall, serializer: KSerializer<T>, data: T) {
        call.respondBytesWriter(
            contentType = SparqlContentType.JsonBindings.withCharset(Charsets.UTF_8)
        ) {
            @OptIn(ExperimentalSerializationApi::class)
            json.encodeToStream(serializer, data, this.toOutputStream())
        }
    }

    private suspend fun respondXml(call: ApplicationCall, data: SelectResponse) {
        call.respondOutputStream(
            contentType = SparqlContentType.XmlBindings.withCharset(Charsets.UTF_8)
        ) {
            val writer = this.bufferedWriter()
            writer.write("<?xml version=\"1.0\"?><sparql xmlns=\"http://www.w3.org/2005/sparql-results#\"><head>")
            data.head.variables.forEach {
                writer.write("<variable name=\"")
                writer.write(it)
                writer.write("\"/>")
            }
            writer.write("</head><results>")
            data.results.bindings.forEach { solution ->
                writer.write("<result>")
                solution.forEach { (name, data) ->
                    val value = data["value"] ?: return@forEach
                    writer.write("<binding name=\"")
                    writer.write(name)
                    writer.write("\"><")
                    val type = data["type"] ?: throw IllegalStateException("Malformed SelectResponse!")
                    writer.write(type)
                    if (type == "literal") {
                        if ("xml:lang" in data) {
                            writer.write(" xml:lang=\"")
                            writer.write(data["xml:lang"]!!)
                            writer.write("\">")
                        } else if ("datatype" in data) {
                            writer.write(" datatype=\"")
                            writer.write(data["datatype"]!!)
                            writer.write("\">")
                        } else {
                            writer.write(">")
                        }
                    } else {
                        writer.write(">")
                    }
                    writer.write(value)
                    writer.write("</")
                    writer.write(type)
                    writer.write("></binding>")
                }
                writer.write("</result>")
            }
            writer.write("</results></sparql>")
            writer.flush()
            writer.close()
        }
    }

}
