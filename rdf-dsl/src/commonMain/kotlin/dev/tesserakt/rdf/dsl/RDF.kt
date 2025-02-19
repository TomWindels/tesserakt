package dev.tesserakt.rdf.dsl

import dev.tesserakt.rdf.ontology.RDF
import dev.tesserakt.rdf.types.Quad
import dev.tesserakt.rdf.types.Quad.Companion.asLiteralTerm
import dev.tesserakt.rdf.types.Quad.Companion.asNamedTerm
import kotlin.jvm.JvmInline

@Suppress("NOTHING_TO_INLINE", "PropertyName", "unused")
class RDF internal constructor(
    private val environment: Environment,
    val consumer: Consumer
) {

    var _blank_index = 0

    internal val prefixes = mutableMapOf<String, String>()

    interface Consumer {

        /** Regular triple receiver **/
        fun process(
            subject: Quad.NamedTerm,
            predicate: Quad.NamedTerm,
            `object`: Quad.Term,
            graph: Quad.Graph = Quad.DefaultGraph,
        )

        /** Blank-node triple receiver **/
        fun process(
            subject: Quad.BlankTerm,
            predicate: Quad.NamedTerm,
            `object`: Quad.Term,
            graph: Quad.Graph = Quad.DefaultGraph,
        )

        /** A method processing incoming RDF lists, returning a term pointing to that list **/
        // default impl creates blank nodes acting like the list, no optimisation in representation (like turtle)
        //  out of the box
        fun process(context: dev.tesserakt.rdf.dsl.RDF, list: List): Quad.Term = with(context) {
            return if (list.data.isEmpty()) {
                RDF.nil
            } else {
                val first = genBlankNodeId()
                var current = first
                val iter = list.data.iterator()
                // actually filling in the data
                current has RDF.first being iter.next()
                while (iter.hasNext()) {
                    val next = genBlankNodeId()
                    next has RDF.first being iter.next()
                    current has RDF.rest being next
                    current = next
                }
                // ending the list
                current has RDF.rest being RDF.nil
                // returning the term pointing to the start of the list
                first
            }
        }

    }

    open class Environment(
        val path: String
    ) {
        val Quad.NamedTerm.relativePath: String
            get() = value.removePrefix(path)
    }

    class Prefix(
        private val prefix: String,
        private val base: String,
    ) {
        operator fun invoke(iri: String) = Quad.NamedTerm("$base$iri")
    }

    val type get() = RDF.type

    fun prefix(prefix: String, base: String) = Prefix(prefix, base)
        .also { prefixes[prefix] = base }

    /** Creates an environment-aware `NamedTerm`, e.g. "shape" on "localhost:3000/" becomes `http://localhost:3000/shape` **/
    fun local(name: String) = "${environment.path}$name".asNamedTerm()

    infix fun Quad.NamedTerm.has(predicate: Quad.NamedTerm) = Statement(this, predicate)

    infix fun Quad.BlankTerm.has(predicate: Quad.NamedTerm) = BlankStatement(this, predicate)

    inner class Statement(val _s: Quad.NamedTerm, val _p: Quad.NamedTerm) {

        inline infix fun being(literal: Int) = consumer.process(_s, _p, literal.asLiteralTerm())

        inline infix fun being(literal: Long) = consumer.process(_s, _p, literal.asLiteralTerm())

        inline infix fun being(literal: Float) = consumer.process(_s, _p, literal.asLiteralTerm())

        inline infix fun being(literal: Double) = consumer.process(_s, _p, literal.asLiteralTerm())

        inline infix fun being(value: Quad.Term) = consumer.process(_s, _p, value)

        inline infix fun being(blank: Blank) = consumer.process(_s, _p, blank._name)

        inline infix fun being(multiple: Multiple) {
            multiple.data.forEach { term ->
                consumer.process(subject = _s, predicate = _p, `object`= term)
            }
        }

        inline infix fun being(list: List) = consumer.process(_s, _p, consumer.process(this@RDF, list))

    }

    fun graph(term: Quad.NamedTerm, producer: dev.tesserakt.rdf.dsl.RDF.() -> Unit) {
        graph(term as Quad.Graph, producer)
    }

    fun graph(iri: String, producer: dev.tesserakt.rdf.dsl.RDF.() -> Unit) {
        graph(iri.asNamedTerm(), producer)
    }

    fun graph(identifier: Quad.BlankTerm, producer: dev.tesserakt.rdf.dsl.RDF.() -> Unit) {
        graph(identifier as Quad.Graph, producer)
    }

    private fun graph(name: Quad.Graph, producer: dev.tesserakt.rdf.dsl.RDF.() -> Unit) {
        RDF(environment, consumer = object: Consumer {
            override fun process(subject: Quad.NamedTerm, predicate: Quad.NamedTerm, `object`: Quad.Term, graph: Quad.Graph) {
                consumer.process(subject, predicate, `object`, name)
            }

            override fun process(subject: Quad.BlankTerm, predicate: Quad.NamedTerm, `object`: Quad.Term, graph: Quad.Graph) {
                consumer.process(subject, predicate, `object`, name)
            }
        }).apply(producer)
    }

    inner class BlankStatement(val _s: Quad.BlankTerm, val _p: Quad.NamedTerm) {

        inline infix fun being(literal: Int) = consumer.process(_s, _p, literal.asLiteralTerm())

        inline infix fun being(literal: Long) = consumer.process(_s, _p, literal.asLiteralTerm())

        inline infix fun being(literal: Float) = consumer.process(_s, _p, literal.asLiteralTerm())

        inline infix fun being(literal: Double) = consumer.process(_s, _p, literal.asLiteralTerm())

        inline infix fun being(value: Quad.Term) = consumer.process(_s, _p, value)

        inline infix fun being(blank: Blank) = consumer.process(_s, _p, blank._name)

        inline infix fun being(multiple: Multiple) {
            multiple.data.forEach { term ->
                consumer.process(subject = _s, predicate = _p, `object`= term)
            }
        }

        inline infix fun being(list: List) = consumer.process(_s, _p, consumer.process(this@RDF, list))

    }

    inner class Blank(val _name: Quad.BlankTerm) {

        inline infix fun Quad.NamedTerm.being(literal: Int) {
            consumer.process(subject = _name, predicate = this, `object`= literal.asLiteralTerm())
        }


        inline infix fun Quad.NamedTerm.being(literal: Long) {
            consumer.process(subject = _name, predicate = this, `object`= literal.asLiteralTerm())
        }

        inline infix fun Quad.NamedTerm.being(literal: Float) {
            consumer.process(subject = _name, predicate = this, `object`= literal.asLiteralTerm())
        }

        inline infix fun Quad.NamedTerm.being(literal: Double) {
            consumer.process(subject = _name, predicate = this, `object`= literal.asLiteralTerm())
        }

        inline infix fun Quad.NamedTerm.being(term: Quad.Term) {
            consumer.process(subject = _name, predicate = this, `object`= term)
        }

        inline infix fun Quad.NamedTerm.being(blank: Blank) {
            consumer.process(subject = _name, predicate = this, `object`= blank._name)
        }

        inline infix fun Quad.NamedTerm.being(multiple: Multiple) {
            multiple.data.forEach { term ->
                consumer.process(subject = _name, predicate = this, `object`= term)
            }
        }

        inline infix fun Quad.NamedTerm.being(list: List) {
            consumer.process(subject = _name, predicate = this, `object`= consumer.process(this@RDF, list))
        }

    }

    @JvmInline
    value class List internal constructor(val data: Array<out Quad.Term>)

    @JvmInline
    value class Multiple internal constructor(val data: Array<out Quad.Term>)

    inline fun blank(block: Blank.() -> Unit): Quad.BlankTerm {
        return Blank(genBlankNodeId()).apply(block)._name
    }

    inline fun genBlankNodeId() = Quad.BlankTerm(_blank_index++)

    fun list(data: Collection<Quad.Term>) = List(data = data.toTypedArray())

    fun list(vararg data: Quad.Term) = List(data)

    fun multiple(data: Collection<Quad.Term>) = Multiple(data.toTypedArray())

    fun multiple(vararg data: Quad.Term) = Multiple(data)

    operator fun Iterable<Quad>.unaryPlus() = forEach { quad ->
        when (val s = quad.s) {
            is Quad.BlankTerm -> consumer.process(subject = s, predicate = quad.p, `object` = quad.o, graph = quad.g)
            is Quad.NamedTerm -> consumer.process(subject = s, predicate = quad.p, `object` = quad.o, graph = quad.g)
            is Quad.Literal -> throw IllegalStateException()
        }
    }

    companion object

}
