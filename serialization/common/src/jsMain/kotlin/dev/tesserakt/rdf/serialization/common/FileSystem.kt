package dev.tesserakt.rdf.serialization.common

@JsModule("fs")
@JsNonModule
internal external object FileSystem {

    fun readFileSync(path: String, opts: dynamic): String

    fun createReadStream(path: String, opts: dynamic): dynamic

}
