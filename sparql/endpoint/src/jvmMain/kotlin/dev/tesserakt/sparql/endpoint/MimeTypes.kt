package dev.tesserakt.sparql.endpoint

import io.ktor.http.*


val SelectQueryType = ContentType(contentType = "application", contentSubtype = "sparql-query")
val UpdateQueryType = ContentType(contentType = "application", contentSubtype = "sparql-update")
val ResponseMimeType = ContentType(contentType = "application", contentSubtype = "sparql-results+json")
