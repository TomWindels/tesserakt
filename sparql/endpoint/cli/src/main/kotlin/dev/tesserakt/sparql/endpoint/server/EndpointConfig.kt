package dev.tesserakt.sparql.endpoint.server

data class EndpointConfig(
    val port: Int,
    val path: String,
    val useCaching: Boolean
) {

    override fun toString() = "port=$port, path=$path, cache=${if (useCaching) "enabled" else "disabled"}"

}
