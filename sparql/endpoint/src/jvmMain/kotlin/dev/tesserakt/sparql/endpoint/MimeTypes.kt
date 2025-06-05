package dev.tesserakt.sparql.endpoint

import io.ktor.http.*


val SparqlSelectQueryPostFormType = ContentType(contentType = "application", contentSubtype = "x-www-form-urlencoded")
val SparqlSelectQueryPostBodyType = ContentType(contentType = "application", contentSubtype = "sparql-query")
val SparqlUpdateQueryType = ContentType(contentType = "application", contentSubtype = "sparql-update")
val SparqlBindingsType = ContentType(contentType = "application", contentSubtype = "sparql-results+json")
