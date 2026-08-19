package dev.tesserakt.rdf.dsl

import dev.tesserakt.rdf.ontology.Ontology
import dev.tesserakt.rdf.ontology.RDF
import dev.tesserakt.rdf.types.Quad
import kotlin.jvm.JvmInline

@Suppress("NOTHING_TO_INLINE", "unused", "FunctionName")
class RDFContext(
    val environment: Environment,
    val consumer: Consumer
) {

    private var blankIndex = 0

    internal val prefixes = mutableMapOf<String, String>()

    interface Consumer {

        /** Regular triple receiver **/
        fun process(
            subject: Quad.Subject,
            predicate: Quad.Predicate,
            `object`: Quad.Object,
            graph: Quad.Graph = Quad.DefaultGraph,
        )

        /** Blank-node triple receiver **/
        fun process(
            subject: Quad.BlankTerm,
            predicate: Quad.Predicate,
            `object`: Quad.Object,
            graph: Quad.Graph = Quad.DefaultGraph,
        )

        /** A method processing incoming RDF lists, returning a term pointing to that list **/
        // default impl creates blank nodes acting like the list, no optimisation in representation (like turtle)
        //  out of the box
        fun process(context: RDFContext, list: List): Quad.Object = with(context) {
            return if (list.data.isEmpty()) {
                RDF.nil
            } else {
                val first = Quad.BlankTerm()
                var current = first
                val iter = list.data.iterator()
                // actually filling in the data
                (current) (RDF.first) (iter.next())
                while (iter.hasNext()) {
                    val next = Quad.BlankTerm()
                    (next) (RDF.first) (iter.next())
                    (current) (RDF.rest) (next)
                    current = next
                }
                // ending the list
                (current) (RDF.rest) (RDF.nil)
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
        val name: String,
        val baseUri: String,
    )

    fun prefix(name: String, baseUri: String) = Prefix(name, baseUri)

    /** Creates an environment-aware `NamedTerm`, e.g. "shape" on "localhost:3000/" becomes `http://localhost:3000/shape` **/
    fun local(name: String) = Quad.NamedTerm("${environment.path}$name")

    operator fun Prefix.div(path: String): Quad.NamedTerm {
        prefixes[this.name] = this.baseUri
        return Quad.NamedTerm("${this.baseUri}$path")
    }

    operator fun Ontology.div(path: String): Quad.NamedTerm {
        prefixes[this.prefix] = this.base_uri
        return this.invoke(path)
    }

    inline operator fun Quad.Graph.invoke(block: RDFContext.() -> Unit) {
        val activeGraph = this@invoke
        RDFContext(environment, consumer = object : Consumer {
            override fun process(
                subject: Quad.Subject,
                predicate: Quad.Predicate,
                `object`: Quad.Object,
                graph: Quad.Graph
            ) {
                consumer.process(subject, predicate, `object`, activeGraph)
            }

            override fun process(
                subject: Quad.BlankTerm,
                predicate: Quad.Predicate,
                `object`: Quad.Object,
                graph: Quad.Graph
            ) {
                consumer.process(subject, predicate, `object`, activeGraph)
            }
        }).apply(block)
    }

    operator fun Quad.Subject.invoke(predicate: Quad.Predicate): Statement {
        return Statement(s = this, p = predicate)
    }

    infix fun Quad.Subject.a(term: Quad.Object) {
        consumer.process(this, RDF.type, term)
    }

    infix fun String.`^^`(type: Quad.NamedTerm): Quad.Literal {
        return Quad.Literal(value = this, type = type)
    }

    operator fun String.rem(language: String): Quad.Literal {
        return Quad.Literal(value = this, language = language)
    }

    infix fun BlankNodeContext.a(term: Quad.Object) {
        consumer.process(name, RDF.type, term)
    }

    inner class Statement(val s: Quad.Subject, val p: Quad.Predicate) {

        operator fun invoke(literal: Int) {
            consumer.process(s, p, Quad.Literal(literal))
        }

        operator fun invoke(literal: Long) {
            consumer.process(s, p, Quad.Literal(literal))
        }

        operator fun invoke(literal: Float) {
            consumer.process(s, p, Quad.Literal(literal))
        }

        operator fun invoke(literal: Double) {
            consumer.process(s, p, Quad.Literal(literal))
        }

        operator fun invoke(term: Quad.Object) {
            consumer.process(this.s, this.p, term)
        }

        operator fun invoke(term: Quad.Object, vararg other: Quad.Object) {
            consumer.process(this.s, this.p, term)
            other.forEach { term -> consumer.process(this.s, this.p, term) }
        }

        operator fun invoke(terms: kotlin.collections.List<Quad.Object>) {
            terms.forEach { term -> consumer.process(this.s, this.p, term) }
        }

        operator fun invoke(list: List) {
            consumer.process(s, p, consumer.process(this@RDFContext, list))
        }

    }

    inner class BlankNodeContext(val name: Quad.BlankTerm) {

        operator fun Quad.Predicate.invoke(literal: Int) {
            consumer.process(name, this, Quad.Literal(literal))
        }

        operator fun Quad.Predicate.invoke(literal: Long) {
            consumer.process(name, this, Quad.Literal(literal))
        }

        operator fun Quad.Predicate.invoke(literal: Float) {
            consumer.process(name, this, Quad.Literal(literal))
        }

        operator fun Quad.Predicate.invoke(literal: Double) {
            consumer.process(name, this, Quad.Literal(literal))
        }

        operator fun Quad.Predicate.invoke(term: Quad.Object) {
            consumer.process(name, this, term)
        }

        operator fun Quad.Predicate.invoke(terms: kotlin.collections.List<Quad.Object>) {
            terms.forEach { term -> consumer.process(this@BlankNodeContext.name, this, term) }
        }

        operator fun Quad.Predicate.invoke(list: List) {
            consumer.process(this@BlankNodeContext.name, this, consumer.process(this@RDFContext, list))
        }

        inline infix fun Quad.Predicate.blank(block: BlankNodeContext.() -> Unit) {
            val subj = Quad.BlankTerm()
            consumer.process(this@BlankNodeContext.name, this, subj)
            block(BlankNodeContext(subj))
        }

    }

    @JvmInline
    value class List internal constructor(val data: Collection<Quad.Object>)

    fun Quad.Companion.BlankTerm() = Quad.BlankTerm(blankIndex++)

    fun list(data: Collection<Quad.Object>) = List(data = data)

    fun list(vararg data: Quad.Object) = List(data.toList())

    operator fun Iterable<Quad>.unaryPlus() = forEach { quad ->
        when (val s = quad.s) {
            is Quad.BlankTerm -> consumer.process(subject = s, predicate = quad.p, `object` = quad.o, graph = quad.g)
            is Quad.NamedTerm -> consumer.process(subject = s, predicate = quad.p, `object` = quad.o, graph = quad.g)
        }
    }

    inline infix fun Statement.blank(block: BlankNodeContext.() -> Unit) {
        val subj = Quad.BlankTerm()
        consumer.process(this.s, this.p, subj)
        block(BlankNodeContext(subj))
    }

    infix fun Statement.list(elements: Collection<Quad.Object>) {
        val list = List(elements)
        consumer.process(s, p, consumer.process(this@RDFContext, list))
    }

    companion object

}
