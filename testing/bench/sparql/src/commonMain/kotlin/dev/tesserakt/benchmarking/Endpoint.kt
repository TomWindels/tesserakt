package dev.tesserakt.benchmarking

import kotlin.jvm.JvmInline

sealed interface Endpoint {

    val queryUrl: String

    @JvmInline
    value class Immutable(
        override val queryUrl: String
    ) : Endpoint {

        init {
            require(queryUrl.startsWith("http://localhost:"))
        }

        override fun toString() = queryUrl
            .substringAfter("localhost:")
            .replace('/', '_')

    }

    data class Mutable(
        override val queryUrl: String,
        val updateUrl: String,
        val token: String?,
    ) : Endpoint {

        init {
            require(queryUrl.startsWith("http://localhost:"))
            require(updateUrl.startsWith("http://localhost:"))
        }

        override fun toString() = "endpoint_" + queryUrl
            .substringAfter("localhost:")
            .replace('/', '_')

    }

}
