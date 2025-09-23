package dev.tesserakt.sparql.endpoint.server

import java.io.File

data class EndpointConfig(
    val port: Int,
    val path: String,
    val useCaching: Boolean,
    val verbose: Boolean,
    val start: File?,
) {

    override fun toString() = "port=$port, path=$path, cache=${if (useCaching) "enabled" else "disabled"}, startFilePath=`${start?.path}`, verbose=$verbose"

}
