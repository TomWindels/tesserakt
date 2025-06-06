package dev.tesserakt.sparql.endpoint.core

import io.ktor.http.*


object SparqlContentType {

    val SelectPostForm = ContentType(contentType = "application", contentSubtype = "x-www-form-urlencoded")
    val SelectPostBody = ContentType(contentType = "application", contentSubtype = "sparql-query")

    val UpdateQuery = ContentType(contentType = "application", contentSubtype = "sparql-update")

    val JsonBindings = ContentType(contentType = "application", contentSubtype = "sparql-results+json")

}
