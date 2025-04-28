package dev.tesserakt.benchmarking

import dev.tesserakt.rdf.trig.serialization.SimpleFormatter
import dev.tesserakt.rdf.trig.serialization.TriGSerializer
import dev.tesserakt.rdf.types.SnapshotStore
import tech.oxfordsemantic.jrdfox.Prefixes
import tech.oxfordsemantic.jrdfox.client.*
import tech.oxfordsemantic.jrdfox.logic.expression.BlankNode
import tech.oxfordsemantic.jrdfox.logic.expression.IRI
import tech.oxfordsemantic.jrdfox.logic.expression.Literal
import java.io.InputStream

class RDFoxReference(private val query: String) : Reference() {
    private var previous = emptyList<MutableList<ResourceValue>>()
    private var current = mutableListOf<MutableList<ResourceValue>>()

    private var checksum = 0

    override fun prepare(diff: SnapshotStore.Diff) {
        connection.newDataStoreConnection("data").use { conn ->
            conn.importData(UpdateType.ADDITION, TriGSerializer.serialize(diff.insertions, SimpleFormatter).toInputStream())
            conn.importData(UpdateType.DELETION, TriGSerializer.serialize(diff.deletions, SimpleFormatter).toInputStream())
        }
    }

    override suspend fun eval() {
        current = mutableListOf()
        connection.newDataStoreConnection("data").use { conn ->
            conn.evaluateQuery(query, emptyMap(), object : QueryAnswerMonitor {
                override fun queryAnswersStarted(p0: Prefixes?, p1: Array<out String>?) {
                    // nothing to do
                }

                override fun processQueryAnswer(value: MutableList<ResourceValue>?, p1: Long) {
                    value ?: return
                    value.forEach { resource ->
                        checksum += resource.checksumValue
                    }
                    current.add(value)
                }

                override fun queryAnswersFinished() {
                    // nothing to do
                }
            })
        }
    }

    override fun finish(): Output {
        val result = compare(current, previous, checksum = checksum)
        checksum = 0
        previous = current
        return result
    }

    override fun close() {
        connection.newDataStoreConnection("data").use { conn ->
            conn.clear(DataStorePart.FACTS)
        }
    }

}

// creating a single connection, making sure it's only configured once upon first use
private val connection by lazy {
    RDFoxServer.newServerConnection("guest", "guest")
        .apply {
            createDataStore("data", emptyMap())
            numberOfThreads = 1
        }
}

private val ResourceValue.checksumValue: Int
    get() = when (val r = this.toResource()) {
        null -> {
            throw IllegalStateException("ResourceValue data type is null!")
        }

        is BlankNode -> {
            r.id.length
        }

        is Literal -> {
            // the lexical form is already the contents inside the `""` notation, e.g. `"true"^^xsd:boolean` has lexical form `true`
            val str = r.lexicalForm
            str.length
        }

        is IRI -> {
            r.iri.length
        }

        else -> {
            throw IllegalStateException("Unexpected resource type `${r::class.simpleName}` encountered!")
        }
    }

private fun Iterator<String>.toInputStream(): InputStream = object: InputStream() {

    private val src = this@toInputStream
    private var current = if (src.hasNext()) src.next().encodeToByteArray() else byteArrayOf()
    private var i = 0

    override fun read(): Int {
        return when {
            i < current.size -> {
                current[i++].toInt()
            }
            src.hasNext() -> {
                i = 0
                current = src.next().encodeToByteArray()
                read()
            }
            else -> -1
        }
    }
}
