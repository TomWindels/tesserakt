package dev.tesserakt.benchmarking

import dev.tesserakt.rdf.trig.serialization.TriGSerializer
import dev.tesserakt.sparql.benchmark.replay.SnapshotStore
import tech.oxfordsemantic.jrdfox.Prefixes
import tech.oxfordsemantic.jrdfox.client.QueryAnswerMonitor
import tech.oxfordsemantic.jrdfox.client.RDFoxServer
import tech.oxfordsemantic.jrdfox.client.ResourceValue
import tech.oxfordsemantic.jrdfox.client.UpdateType
import tech.oxfordsemantic.jrdfox.logic.expression.BlankNode
import tech.oxfordsemantic.jrdfox.logic.expression.IRI
import tech.oxfordsemantic.jrdfox.logic.expression.Literal

class RDFoxReference(private val query: String) : Reference() {

    private val connection = RDFoxServer.newServerConnection("guest", "guest")
    private var previous = emptyList<MutableList<ResourceValue>>()
    private var current = mutableListOf<MutableList<ResourceValue>>()

    private var checksum = 0

    init {
        connection.createDataStore("data", emptyMap())
        connection.numberOfThreads = 1
    }

    override fun prepare(diff: SnapshotStore.Diff) {
        connection.newDataStoreConnection("data").use { conn ->
            conn.importData(UpdateType.ADDITION, TriGSerializer.serialize(diff.insertions))
            conn.importData(UpdateType.DELETION, TriGSerializer.serialize(diff.deletions))
        }
    }

    override fun eval() {
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
        connection.close()
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
            val str = r.lexicalForm
            require(str.startsWith('"')) {
                "Unexpected lexical form for a literal encountered: got `${str}`, expected `\"value\"^^<type>`"
            }
            str.indexOf('"', 2) - 1
        }

        is IRI -> {
            r.iri.length
        }

        else -> {
            throw IllegalStateException("Unexpected resource type `${r::class.simpleName}` encountered!")
        }
    }
