package dev.tesserakt.rdf.serialization.common

import dev.tesserakt.rdf.ontology.Ontology
import dev.tesserakt.rdf.types.Quad
import kotlin.jvm.JvmInline


@JvmInline
value class Prefixes(private val map: Map<String /* prefix */, String /* uri */>): Collection<Map.Entry<String, String>> {

    data class PrefixedTerm(
        val prefix: String,
        val value: String,
    )

    fun interface ValueTransformer {
        /**
         * A transform function responsible for converting the provided [content] into a valid prefix value, or null
         *  if such a conversion is not possible
         */
        fun makeValidPrefixValue(content: String): String?
    }

    /**
     * Default prefix value validator implementation, checks the resulting values according to the Turtle (and therefore
     *  TriG) spec
     */
    data object DefaultValueValidator: ValueTransformer {
        override fun makeValidPrefixValue(content: String): String? {
            // leading `:` is not allowed
            if (content.isEmpty() || content.first() == ':') {
                return null
            }
            var i = -1
            // reserving some additional space, just in case
            val result = StringBuilder(content.length + 5)
            // https://www.w3.org/TR/turtle/#h_note_2
            while (i < content.length - 1) {
                ++i
                /* valid cases */
                // valid characters, if `:`, it's already guaranteed to be at i > 0
                if (content[i].isLetterOrDigit() || content[i] == ':') {
                    result.append(content[i])
                    continue
                }
                // supporting sequences such as `%55`; see https://www.w3.org/TR/turtle/#h_note_5
                if (i < content.length - 2 && content[i] == '%' && content[i + 1].isHexDecimal() && content[i + 2].isHexDecimal()) {
                    result.append(content[i])
                    // even though adding the other ones wouldn't be a problem with the other existing checks, this is
                    //  a bit more direct in intent
                    result.append(content[i + 1])
                    result.append(content[i + 2])
                    i += 2
                    continue
                }
                // creating valid escapes
                if (content[i] in "~.-!$&'()*+,;=/?#@%_") {
                    result.append('\\')
                    result.append(content[i])
                    continue
                }
                // otherwise: invalid case
                return null
            }
            // building the final result
            return result.toString()
        }

        private fun Char.isHexDecimal(): Boolean {
            return isDigit() || lowercaseChar() in "abcdef"
        }
    }

    constructor(vararg ontology: Ontology): this(map = ontology.associate { it.prefix to it.base_uri })

    fun format(term: Quad.NamedTerm, transformer: ValueTransformer = DefaultValueValidator): PrefixedTerm? {
        map.forEach { (prefix, uri) ->
            if (term.value.startsWith(uri)) {
                val value = transformer.makeValidPrefixValue(term.value.drop(uri.length))
                if (value != null) {
                    return PrefixedTerm(prefix, value)
                }
                // whilst it's highly unlikely another prefix pair would also fit, but with a valid prefix value,
                //  we're not breaking out of the loop just yet, just in case
            }
        }
        return null
    }

    override fun iterator(): Iterator<Map.Entry<String, String>> {
        return map.iterator()
    }

    override val size: Int
        get() = map.size

    override fun containsAll(elements: Collection<Map.Entry<String, String>>) =
        map.entries.containsAll(elements)

    override fun contains(element: Map.Entry<String, String>) = map.entries.contains(element)

    override fun isEmpty() = map.isEmpty()

    operator fun plus(other: Prefixes) = Prefixes(map + other.map)

    companion object {

        operator fun Map<String, String>.plus(ontology: Ontology) = this.plus(ontology.prefix to ontology.base_uri)

    }

}
